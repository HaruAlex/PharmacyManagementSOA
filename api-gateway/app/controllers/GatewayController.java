package controllers;

import com.ecwid.consul.v1.health.model.HealthService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import integration.consul.ConsulServiceResolver;
import integration.consul.ServiceResolver;
import integration.consul.ServiceDiscoveryException;
import org.apache.pekko.stream.javadsl.Source;
import play.Logger;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import play.libs.typedmap.TypedKey;
import services.GatewayJwtVerifier;
import services.GatewayRouteConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

@Singleton
public class GatewayController extends Controller {
    public static final TypedKey<String> REQUEST_ID = TypedKey.create("gateway-request-id");

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "content-length");

    private final ServiceResolver resolver;
    private final WSClient wsClient;
    private final GatewayRouteConfig routeConfig;
    private final GatewayJwtVerifier jwtVerifier;
    private final int timeoutMillis;
    private final int maxAttempts;

    @Inject
    public GatewayController(
            ServiceResolver resolver,
            WSClient wsClient,
            GatewayRouteConfig routeConfig,
            GatewayJwtVerifier jwtVerifier,
            Config config) {
        this.resolver = resolver;
        this.wsClient = wsClient;
        this.routeConfig = routeConfig;
        this.jwtVerifier = jwtVerifier;
        this.timeoutMillis = config.getInt("gateway.timeout-ms");
        this.maxAttempts = config.getInt("gateway.max-attempts");
        if (timeoutMillis < 1 || maxAttempts < 1) {
            throw new IllegalArgumentException("Gateway timeout and max attempts must be positive");
        }
    }

    public CompletionStage<Result> proxyRoot(Http.Request request) {
        return handleProxy(request);
    }

    public CompletionStage<Result> proxy(Http.Request request, String path) {
        return handleProxy(request);
    }

    private CompletionStage<Result> handleProxy(Http.Request request) {
        String requestId = requestId(request);
        if (!isValidRequestId(requestId)) {
            return CompletableFuture.completedFuture(
                    error(400, "INVALID_REQUEST_ID", "X-Request-Id has an invalid format", UUID.randomUUID().toString()));
        }

        Optional<GatewayRouteConfig.Route> route = routeConfig.findRoute(request.path());
        if (route.isEmpty()) {
            return CompletableFuture.completedFuture(
                    error(404, "ROUTE_NOT_FOUND", "No route is configured for this path", requestId));
        }

        GatewayJwtVerifier.Claims identity = null;
        Optional<String> authorization = request.header("Authorization");
        if (!routeConfig.isPublic(request.path(), request.method()) || authorization.isPresent()) {
            if (authorization.isEmpty() || !authorization.get().regionMatches(true, 0, "Bearer ", 0, 7)) {
                return CompletableFuture.completedFuture(
                        error(401, "UNAUTHORIZED", "A valid Bearer token is required", requestId));
            }
            try {
                identity = jwtVerifier.verify(authorization.get().substring(7).trim());
            } catch (RuntimeException e) {
                return CompletableFuture.completedFuture(
                        error(401, "INVALID_TOKEN", "The access token is invalid or expired", requestId));
            }
        }

        if (identity != null) {
            Optional<Integer> requestedBranch = request.header("X-Branch-Id").map(value -> {
                try {
                    int branchId = Integer.parseInt(value);
                    return branchId > 0 ? branchId : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            });
            if (request.header("X-Branch-Id").isPresent() && requestedBranch.isEmpty()) {
                return CompletableFuture.completedFuture(
                        error(400, "INVALID_BRANCH_ID", "X-Branch-Id must be a positive integer", requestId));
            }
            if (requestedBranch.isPresent() && !identity.getBranchIds().contains(requestedBranch.get())) {
                return CompletableFuture.completedFuture(
                        error(403, "BRANCH_FORBIDDEN", "The requested branch is not assigned to this user", requestId));
            }
            Optional<Set<String>> allowedRoles = routeConfig.allowedRoles(request.path(), request.method());
            if (allowedRoles.isEmpty()) {
                return CompletableFuture.completedFuture(
                        error(403, "ROUTE_POLICY_MISSING", "No role policy is configured for this route", requestId));
            }
            if (!allowedRoles.get().contains(identity.getRole())) {
                return CompletableFuture.completedFuture(
                        error(403, "FORBIDDEN", "The user's role cannot access this route", requestId));
            }
            if (requestedBranch.isEmpty() && identity.getCurrentBranchId() != null
                    && !identity.getBranchIds().contains(identity.getCurrentBranchId())) {
                return CompletableFuture.completedFuture(
                        error(403, "BRANCH_FORBIDDEN", "The current branch is not assigned to this user", requestId));
            }
        }

        return proxyAttempt(
                request,
                route.get(),
                identity,
                authorization.orElse(null),
                request.header("X-Branch-Id").orElse(
                        identity == null || identity.getCurrentBranchId() == null
                                ? null
                                : identity.getCurrentBranchId().toString()),
                requestId,
                new HashSet<>(),
                0);
    }

    public Result notFound(Http.Request request, String path) {
        return error(404, "ROUTE_NOT_FOUND", "No route is configured for this path", requestId(request));
    }

    private String requestId(Http.Request request) {
        String attributeRequestId = request.attrs().getOptional(REQUEST_ID).orElse(null);
        return attributeRequestId != null
                ? attributeRequestId
                : request.header("X-Request-Id").orElseGet(() -> UUID.randomUUID().toString());
    }

    private CompletionStage<Result> proxyAttempt(
            Http.Request request,
            GatewayRouteConfig.Route route,
            GatewayJwtVerifier.Claims identity,
            String authorization,
            String branchId,
            String requestId,
            Set<String> excludedInstanceIds,
            int attempt) {
        final HealthService instance;
        try {
            Optional<HealthService> selected = resolver.resolve(route.getServiceName(), Set.copyOf(excludedInstanceIds));
            if (selected.isEmpty()) {
                return CompletableFuture.completedFuture(
                        error(503, "SERVICE_UNAVAILABLE", "No healthy instance is available", requestId));
            }
            instance = selected.get();
        } catch (ServiceDiscoveryException e) {
            Logger.error("[" + requestId + "] Consul lookup failed for " + route.getServiceName(), e);
            return CompletableFuture.completedFuture(
                    error(503, "SERVICE_DISCOVERY_UNAVAILABLE", "Service discovery is unavailable", requestId));
        } catch (RuntimeException e) {
            Logger.error("[" + requestId + "] Could not resolve " + route.getServiceName(), e);
            return CompletableFuture.completedFuture(
                    error(503, "SERVICE_UNAVAILABLE", "No healthy instance is available", requestId));
        }

        String serviceId;
        String url;
        try {
            serviceId = ConsulServiceResolver.serviceId(instance);
            url = ConsulServiceResolver.baseUrl(instance)
                    + upstreamPath(request.path(), route)
                    + queryString(request.uri());
        } catch (RuntimeException e) {
            Logger.error("[" + requestId + "] Consul returned an invalid instance", e);
            return CompletableFuture.completedFuture(
                    error(502, "INVALID_UPSTREAM", "The selected service instance is invalid", requestId));
        }

        Logger.info("[" + requestId + "] -> " + route.getServiceName() + " " + request.method()
                + " " + url + " [" + serviceId + "]");

        WSRequest wsRequest = wsClient.url(url).setRequestTimeout(timeoutMillis);
        addForwardedHeaders(request, wsRequest, identity, authorization, branchId, requestId);
        org.apache.pekko.util.ByteString body = request.body().asBytes();
        if (body != null) {
            wsRequest.setBody(Source.single(body));
        }

        return wsRequest.execute(request.method()).handle((response, failure) -> {
            if (failure == null && !shouldRetry(response.getStatus())) {
                return CompletableFuture.completedFuture(
                        proxyResponse(response, serviceId, requestId));
            }

            excludedInstanceIds.add(serviceId);
            if (attempt + 1 < maxAttempts) {
                Logger.warn("[" + requestId + "] Retrying " + route.getServiceName()
                        + " after instance " + serviceId + " failed");
                return proxyAttempt(
                        request, route, identity, authorization, branchId,
                        requestId, excludedInstanceIds, attempt + 1).toCompletableFuture();
            }

            if (failure != null) {
                Throwable cause = unwrap(failure);
                Logger.error("[" + requestId + "] Upstream request failed for " + route.getServiceName(), cause);
                if (isTimeout(cause)) {
                    return CompletableFuture.completedFuture(
                            error(504, "UPSTREAM_TIMEOUT", "The upstream service timed out", requestId));
                }
                return CompletableFuture.completedFuture(
                        error(502, "UPSTREAM_FAILURE", "The upstream service could not be reached", requestId));
            }

            Logger.warn("[" + requestId + "] All healthy instances failed for " + route.getServiceName());
            return CompletableFuture.completedFuture(
                    error(503, "SERVICE_UNAVAILABLE", "No healthy instance is available", requestId));
        }).thenCompose(stage -> stage);
    }

    private void addForwardedHeaders(
            Http.Request request,
            WSRequest wsRequest,
            GatewayJwtVerifier.Claims identity,
            String authorization,
            String branchId,
            String requestId) {
        addHeader(request, wsRequest, "Accept");
        addHeader(request, wsRequest, "Content-Type");
        addHeader(request, wsRequest, "Idempotency-Key");
        if (authorization != null) {
            wsRequest.addHeader("Authorization", authorization);
        }
        wsRequest.addHeader("X-Request-Id", requestId);
        if (identity != null) {
            wsRequest.addHeader("X-User-Id", Integer.toString(identity.getUserId()));
            wsRequest.addHeader("X-User-Role", identity.getRole());
        }
        if (branchId != null) {
            wsRequest.addHeader("X-Branch-Id", branchId);
        }
    }

    private void addHeader(Http.Request request, WSRequest wsRequest, String name) {
        request.header(name).ifPresent(value -> wsRequest.addHeader(name, value));
    }

    private Result proxyResponse(WSResponse response, String serviceId, String requestId) {
        int status = response.getStatus();
        if (status == 400) {
            return error(400, "UPSTREAM_VALIDATION_FAILED",
                    "The upstream service rejected the request", requestId)
                    .withHeader("X-Served-By", serviceId);
        }
        if (status == 401) {
            return error(401, "UNAUTHORIZED", "The upstream service rejected authentication", requestId)
                    .withHeader("X-Served-By", serviceId);
        }
        if (status == 403) {
            return error(403, "FORBIDDEN", "The upstream service denied access", requestId)
                    .withHeader("X-Served-By", serviceId);
        }
        if (status == 404) {
            return error(404, "UPSTREAM_NOT_FOUND", "The requested resource was not found", requestId)
                    .withHeader("X-Served-By", serviceId);
        }

        Result result = Results.status(status, response.getBody());
        String contentType = response.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            result = result.as(contentType);
        }
        for (var entry : response.getHeaders().entrySet()) {
            if (HOP_BY_HOP_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            List<String> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                result = result.withHeader(entry.getKey(), String.join(", ", values));
            }
        }
        return result.withHeader("X-Served-By", serviceId)
                .withHeader("X-Request-Id", requestId);
    }

    private Result error(int status, String code, String message, String requestId) {
        ObjectNode body = Json.newObject();
        body.put("success", false);
        body.put("code", code);
        body.put("message", message);
        body.put("requestId", requestId);
        return Results.status(status, body)
                .as("application/json")
                .withHeader("X-Request-Id", requestId);
    }

    private String upstreamPath(String publicPath, GatewayRouteConfig.Route route) {
        String suffix = publicPath.substring(route.getPublicPrefix().length());
        String upstreamPrefix = route.getUpstreamPrefix();
        if (upstreamPrefix.isEmpty()) {
            return suffix.isEmpty() ? "/" : suffix;
        }
        if (suffix.isEmpty() || "/".equals(suffix)) {
            return upstreamPrefix;
        }
        return upstreamPrefix + (suffix.startsWith("/") ? suffix : "/" + suffix);
    }

    private String queryString(String uri) {
        int queryIndex = uri.indexOf('?');
        return queryIndex < 0 ? "" : uri.substring(queryIndex);
    }

    private boolean shouldRetry(int status) {
        return status == 502 || status == 503 || status == 504;
    }

    private boolean isValidRequestId(String requestId) {
        return requestId != null && requestId.length() <= 128
                && requestId.matches("[A-Za-z0-9._:-]+");
    }

    private Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause.getCause() != null)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private boolean isTimeout(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}

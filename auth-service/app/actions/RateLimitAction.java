package actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import domain.ErrorCodes;
import play.libs.Json;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import security.TokenInvalidException;
import security.TokenService;
import services.RateLimitService;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public class RateLimitAction extends Action<RateLimited> {
    private final RateLimitService rateLimitService;
    private final TokenService tokenService;
    private final Config config;

    @Inject
    public RateLimitAction(
            RateLimitService rateLimitService,
            TokenService tokenService,
            Config config) {
        this.rateLimitService = rateLimitService;
        this.tokenService = tokenService;
        this.config = config;
    }

    @Override
    public CompletionStage<Result> call(Http.Request request) {
        RateLimited.Policy policy = configuration.value();
        String prefix = "security.rateLimit." + policy.configKey();
        int windowSeconds = config.getInt(prefix + ".windowSeconds");
        int maxPerIdentity = config.getInt(prefix + ".maxPerIdentity");
        int maxPerIp = config.getInt(prefix + ".maxPerIp");
        String identity = identify(request, policy);

        boolean allowed = rateLimitService.tryAcquire(
                policy.name(),
                request.remoteAddress(),
                identity,
                maxPerIdentity,
                maxPerIp,
                Duration.ofSeconds(windowSeconds));
        if (!allowed) {
            ObjectNode error = Json.newObject();
            error.put("status", Http.Status.TOO_MANY_REQUESTS);
            error.put("code", ErrorCodes.RATE_LIMIT_EXCEEDED);
            error.put("message", "Too many requests. Try again later.");
            return CompletableFuture.completedFuture(
                    Results.status(Http.Status.TOO_MANY_REQUESTS, error)
                            .withHeader("Retry-After", Integer.toString(windowSeconds)));
        }
        return delegate.call(request);
    }

    private String identify(Http.Request request, RateLimited.Policy policy) {
        if (policy == RateLimited.Policy.CHANGE_PASSWORD) {
            String bearer = request.header("Authorization")
                    .filter(header -> header.startsWith("Bearer "))
                    .map(header -> header.substring(7))
                    .orElse("");
            if (bearer.isBlank()) {
                return "missing";
            }
            try {
                return Integer.toString(tokenService.verifyToken(bearer).getUserId());
            } catch (TokenInvalidException e) {
                return "invalid";
            }
        }

        JsonNode body = request.body().asJson();
        if (body == null) {
            return "missing";
        }
        String field = policy == RateLimited.Policy.LOGIN ? "usernameOrEmail" : "refreshToken";
        String value = body.path(field).asText("").trim();
        if (value.isEmpty()) {
            return "missing";
        }
        return policy == RateLimited.Policy.LOGIN
                ? value.toLowerCase(Locale.ROOT)
                : value;
    }
}

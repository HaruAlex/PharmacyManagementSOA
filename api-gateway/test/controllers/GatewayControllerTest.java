package controllers;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ecwid.consul.v1.health.model.HealthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import integration.consul.ServiceResolver;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.jupiter.api.Test;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import play.mvc.Http;
import play.mvc.Result;
import services.GatewayJwtVerifier;
import services.GatewayRouteConfig;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeRequest;

class GatewayControllerTest {
    private static final String SECRET = "a-development-secret-longer-than-32-characters";

    @Test
    void proxyPreservesMethodQueryStatusAndForwardedIdentityHeaders() {
        ServiceResolver resolver = mock(ServiceResolver.class);
        WSClient wsClient = mock(WSClient.class);
        WSRequest wsRequest = mock(WSRequest.class);
        WSResponse wsResponse = mock(WSResponse.class);
        when(resolver.resolve(eq("sales-service"), any(Set.class)))
                .thenReturn(Optional.of(instance("sales-1", "sales", 9005)));
        when(wsClient.url("http://sales:9005/invoices/create?source=web")).thenReturn(wsRequest);
        when(wsRequest.setRequestTimeout(1000)).thenReturn(wsRequest);
        when(wsRequest.addHeader(anyString(), anyString())).thenReturn(wsRequest);
        when(wsRequest.execute("POST")).thenReturn(CompletableFuture.completedFuture(wsResponse));
        when(wsResponse.getStatus()).thenReturn(201);
        when(wsResponse.getBody()).thenReturn("{\"created\":true}");
        when(wsResponse.getContentType()).thenReturn("application/json");
        when(wsResponse.getHeaders()).thenReturn(Map.of());

        GatewayController controller = controller(resolver, wsClient);
        String accessToken = token("SP", List.of(3));
        Http.Request request = fakeRequest("POST", "/api/sales/invoices/create?source=web")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Branch-Id", "3")
                .header("X-Request-Id", "req-123")
                .bodyJson(Json.parse("{\"invoiceId\":9}"))
                .build();

        Result result = controller.proxy(request, "sales/invoices/create").toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(201);
        assertThat(contentAsString(result)).isEqualTo("{\"created\":true}");
        verify(wsRequest).execute("POST");
        verify(wsRequest).addHeader("Authorization", "Bearer " + accessToken);
        verify(wsRequest).addHeader("X-User-Id", "42");
        verify(wsRequest).addHeader("X-User-Role", "SP");
        verify(wsRequest).addHeader("X-Branch-Id", "3");
        verify(wsRequest).addHeader("X-Request-Id", "req-123");
        verify(wsRequest).setBody(any(Source.class));
    }

    @Test
    void protectedRouteWithoutTokenReturnsStandard401Envelope() {
        GatewayController controller = controller(mock(ServiceResolver.class), mock(WSClient.class));
        Http.Request request = fakeRequest("GET", "/api/sales/shifts/current")
                .header("X-Request-Id", "req-no-token")
                .build();

        Result result = controller.proxy(request, "sales/shifts/current").toCompletableFuture().join();
        JsonNode body = Json.parse(contentAsString(result));

        assertThat(result.status()).isEqualTo(401);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.path("requestId").asText()).isEqualTo("req-no-token");
    }

    @Test
    void branchOutsideTokenClaimsReturns403BeforeServiceDiscovery() {
        ServiceResolver resolver = mock(ServiceResolver.class);
        GatewayController controller = controller(resolver, mock(WSClient.class));
        Http.Request request = fakeRequest("GET", "/api/sales/shifts/current")
                .header("Authorization", "Bearer " + token("SP", List.of(3)))
                .header("X-Branch-Id", "4")
                .build();

        Result result = controller.proxy(request, "sales/shifts/current").toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(403);
        verify(resolver, org.mockito.Mockito.never()).resolve(anyString(), any(Set.class));
    }

    @Test
    void noHealthyInstanceReturnsStandard503Envelope() {
        ServiceResolver resolver = mock(ServiceResolver.class);
        when(resolver.resolve(eq("sales-service"), any(Set.class))).thenReturn(Optional.empty());
        GatewayController controller = controller(resolver, mock(WSClient.class));
        Http.Request request = fakeRequest("GET", "/api/sales/shifts/current")
                .header("Authorization", "Bearer " + token("SP", List.of(3)))
                .header("X-Request-Id", "req-unavailable")
                .build();

        Result result = controller.proxy(request, "sales/shifts/current").toCompletableFuture().join();
        JsonNode body = Json.parse(contentAsString(result));

        assertThat(result.status()).isEqualTo(503);
        assertThat(body.path("code").asText()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(body.path("requestId").asText()).isEqualTo("req-unavailable");
    }

    @Test
    void failedInstanceIsExcludedAndRequestRetriesAnotherInstance() {
        ServiceResolver resolver = mock(ServiceResolver.class);
        WSClient wsClient = mock(WSClient.class);
        WSRequest firstRequest = mock(WSRequest.class);
        WSRequest secondRequest = mock(WSRequest.class);
        WSResponse response = mock(WSResponse.class);
        when(resolver.resolve(eq("sales-service"), any(Set.class))).thenAnswer(invocation -> {
            Set<String> excluded = invocation.getArgument(1);
            return Optional.of(excluded.contains("sales-1")
                    ? instance("sales-2", "sales-two", 9005)
                    : instance("sales-1", "sales-one", 9005));
        });
        when(wsClient.url("http://sales-one:9005/shifts/current")).thenReturn(firstRequest);
        when(wsClient.url("http://sales-two:9005/shifts/current")).thenReturn(secondRequest);
        when(firstRequest.setRequestTimeout(1000)).thenReturn(firstRequest);
        when(secondRequest.setRequestTimeout(1000)).thenReturn(secondRequest);
        when(firstRequest.addHeader(anyString(), anyString())).thenReturn(firstRequest);
        when(secondRequest.addHeader(anyString(), anyString())).thenReturn(secondRequest);
        when(firstRequest.execute("GET")).thenReturn(CompletableFuture.failedFuture(new IOException("offline")));
        when(secondRequest.execute("GET")).thenReturn(CompletableFuture.completedFuture(response));
        when(response.getStatus()).thenReturn(200);
        when(response.getBody()).thenReturn("{\"ok\":true}");
        when(response.getContentType()).thenReturn("application/json");
        when(response.getHeaders()).thenReturn(Map.of());
        GatewayController controller = controller(resolver, wsClient);
        Http.Request request = fakeRequest("GET", "/api/sales/shifts/current")
                .header("Authorization", "Bearer " + token("SP", List.of(3)))
                .build();

        Result result = controller.proxy(request, "sales/shifts/current").toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(200);
        verify(resolver, org.mockito.Mockito.times(2)).resolve(eq("sales-service"), any(Set.class));
        verify(resolver).resolve(eq("sales-service"), argThat(ids -> ids.contains("sales-1")));
    }

    private GatewayController controller(ServiceResolver resolver, WSClient wsClient) {
        Config config = ConfigFactory.parseString(
                "gateway.timeout-ms=1000\n"
                        + "gateway.max-attempts=2\n"
                        + "gateway.routes=[{public-prefix=\"/api/sales\", service-name=\"sales-service\", upstream-prefix=\"\"}]\n"
                        + "gateway.public-routes=[]\n"
                        + "gateway.role-rules=[{path-prefix=\"/api/sales\", methods=[\"GET\", \"POST\"], roles=[\"AD\", \"SP\"]}]\n"
                        + "jwt.secret=\"" + SECRET + "\"\n"
                        + "jwt.clock-skew-seconds=0");
        return new GatewayController(
                resolver,
                wsClient,
                new GatewayRouteConfig(config),
                new GatewayJwtVerifier(config),
                config);
    }

    private String token(String role, List<Integer> branches) {
        return JWT.create()
                .withSubject("42")
                .withClaim("role", role)
                .withClaim("branch_ids", branches)
                .withClaim("current_branch_id", branches.get(0))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(120)))
                .sign(Algorithm.HMAC256(SECRET));
    }

    private HealthService instance(String id, String address, int port) {
        HealthService.Service service = new HealthService.Service();
        service.setId(id);
        service.setAddress(address);
        service.setPort(port);
        HealthService instance = new HealthService();
        instance.setService(service);
        return instance;
    }
}

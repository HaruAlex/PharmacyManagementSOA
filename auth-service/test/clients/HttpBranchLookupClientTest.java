package clients;

import com.ecwid.consul.v1.health.model.HealthService;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import integration.consul.ServiceResolver;
import org.junit.jupiter.api.Test;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.util.concurrent.CompletableFuture;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class HttpBranchLookupClientTest {
    @Test
    void getAllActiveBranchIds_withCatalogResponse_shouldReturnUniqueIds() {
        WSClient wsClient = mock(WSClient.class);
        ServiceResolver resolver = resolverWithCatalogInstance();
        WSRequest request = mock(WSRequest.class);
        WSResponse response = mock(WSResponse.class);
        when(wsClient.url("http://catalog:9002/api/branches/active")).thenReturn(request);
        when(request.setRequestTimeout(5000)).thenReturn(request);
        when(request.get()).thenReturn(CompletableFuture.completedFuture(response));
        when(response.getStatus()).thenReturn(200);
        when(response.asJson()).thenReturn(Json.parse("{\"branchIds\":[1,2,2,3]}"));

        HttpBranchLookupClient client = new HttpBranchLookupClient(wsClient, config(), resolver);

        assertThat(client.getAllActiveBranchIds().toCompletableFuture().join())
                .containsExactly(1, 2, 3);
    }

    @Test
    void getAllActiveBranchIds_whenCatalogReturnsError_shouldFailExplicitly() {
        WSClient wsClient = mock(WSClient.class);
        ServiceResolver resolver = resolverWithCatalogInstance();
        WSRequest request = mock(WSRequest.class);
        WSResponse response = mock(WSResponse.class);
        when(wsClient.url(anyString())).thenReturn(request);
        when(request.get()).thenReturn(CompletableFuture.completedFuture(response));
        when(response.getStatus()).thenReturn(503);

        when(request.setRequestTimeout(5000)).thenReturn(request);
        HttpBranchLookupClient client = new HttpBranchLookupClient(wsClient, config(), resolver);

        assertThatThrownBy(() -> client.getAllActiveBranchIds().toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 503");
    }

    @Test
    void getAllActiveBranchIds_withMalformedIds_shouldFailExplicitly() {
        WSClient wsClient = mock(WSClient.class);
        ServiceResolver resolver = resolverWithCatalogInstance();
        WSRequest request = mock(WSRequest.class);
        WSResponse response = mock(WSResponse.class);
        when(wsClient.url(anyString())).thenReturn(request);
        when(request.get()).thenReturn(CompletableFuture.completedFuture(response));
        when(response.getStatus()).thenReturn(200);
        when(response.asJson()).thenReturn(Json.parse("{\"branchIds\":[1,\"2\"]}"));

        when(request.setRequestTimeout(5000)).thenReturn(request);
        HttpBranchLookupClient client = new HttpBranchLookupClient(wsClient, config(), resolver);

        assertThatThrownBy(() -> client.getAllActiveBranchIds().toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-integer branch ID");
    }

    private Config config() {
        return ConfigFactory.parseString(
                "catalog.service.name=\"catalog-service\"\n"
                        + "catalog.service.timeout-ms=5000\n"
                        + "catalog.service.activeBranchesPath=\"/api/branches/active\"");
    }

    private ServiceResolver resolverWithCatalogInstance() {
        ServiceResolver resolver = mock(ServiceResolver.class);
        HealthService instance = new HealthService();
        HealthService.Service service = new HealthService.Service();
        service.setAddress("catalog");
        service.setPort(9002);
        service.setId("catalog-1");
        instance.setService(service);
        when(resolver.resolve("catalog-service")).thenReturn(Optional.of(instance));
        return resolver;
    }
}

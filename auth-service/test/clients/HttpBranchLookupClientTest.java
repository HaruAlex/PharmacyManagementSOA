package clients;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class HttpBranchLookupClientTest {
    @Test
    void getAllActiveBranchIds_withCatalogResponse_shouldReturnUniqueIds() {
        WSClient wsClient = mock(WSClient.class);
        WSRequest request = mock(WSRequest.class);
        WSResponse response = mock(WSResponse.class);
        when(wsClient.url("http://catalog/api/branches/active")).thenReturn(request);
        when(request.get()).thenReturn(CompletableFuture.completedFuture(response));
        when(response.getStatus()).thenReturn(200);
        when(response.asJson()).thenReturn(Json.parse("{\"branchIds\":[1,2,2,3]}"));

        HttpBranchLookupClient client = new HttpBranchLookupClient(wsClient, config());

        assertThat(client.getAllActiveBranchIds().toCompletableFuture().join())
                .containsExactly(1, 2, 3);
    }

    @Test
    void getAllActiveBranchIds_whenCatalogReturnsError_shouldFailExplicitly() {
        WSClient wsClient = mock(WSClient.class);
        WSRequest request = mock(WSRequest.class);
        WSResponse response = mock(WSResponse.class);
        when(wsClient.url(anyString())).thenReturn(request);
        when(request.get()).thenReturn(CompletableFuture.completedFuture(response));
        when(response.getStatus()).thenReturn(503);

        HttpBranchLookupClient client = new HttpBranchLookupClient(wsClient, config());

        assertThatThrownBy(() -> client.getAllActiveBranchIds().toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 503");
    }

    @Test
    void getAllActiveBranchIds_withMalformedIds_shouldFailExplicitly() {
        WSClient wsClient = mock(WSClient.class);
        WSRequest request = mock(WSRequest.class);
        WSResponse response = mock(WSResponse.class);
        when(wsClient.url(anyString())).thenReturn(request);
        when(request.get()).thenReturn(CompletableFuture.completedFuture(response));
        when(response.getStatus()).thenReturn(200);
        when(response.asJson()).thenReturn(Json.parse("{\"branchIds\":[1,\"2\"]}"));

        HttpBranchLookupClient client = new HttpBranchLookupClient(wsClient, config());

        assertThatThrownBy(() -> client.getAllActiveBranchIds().toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-integer branch ID");
    }

    private Config config() {
        return ConfigFactory.parseString(
                "catalog.service.baseUrl=\"http://catalog/\"\n"
                        + "catalog.service.activeBranchesPath=\"/api/branches/active\"");
    }
}

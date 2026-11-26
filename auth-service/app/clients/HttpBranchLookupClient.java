package clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletionStage;

@Singleton
public class HttpBranchLookupClient implements BranchLookupClient {
    private final WSClient wsClient;
    private final String activeBranchesUrl;

    @Inject
    public HttpBranchLookupClient(WSClient wsClient, Config config) {
        this.wsClient = wsClient;
        String baseUrl = config.getString("catalog.service.baseUrl").replaceAll("/+$", "");
        String path = config.getString("catalog.service.activeBranchesPath");
        this.activeBranchesUrl = baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    @Override
    public CompletionStage<List<Integer>> getAllActiveBranchIds() {
        return wsClient.url(activeBranchesUrl).get().thenApply(this::readActiveBranchIds);
    }

    @Override
    public CompletionStage<Boolean> isBranchActive(Integer branchId) {
        if (branchId == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        return getAllActiveBranchIds().thenApply(branchIds -> branchIds.contains(branchId));
    }

    @Override
    public CompletionStage<Boolean> validateBranchIds(List<Integer> branchIds) {
        if (branchIds == null || branchIds.isEmpty()
                || branchIds.stream().anyMatch(java.util.Objects::isNull)) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        return getAllActiveBranchIds().thenApply(activeIds -> activeIds.containsAll(branchIds));
    }

    private List<Integer> readActiveBranchIds(WSResponse response) {
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new IllegalStateException(
                    "Catalog Service returned HTTP " + response.getStatus()
                            + " while looking up active branches");
        }

        JsonNode body = response.asJson();
        if (body == null || !body.isObject()) {
            throw new IllegalStateException("Catalog Service response must be a JSON object");
        }
        JsonNode branchIds = body.get("branchIds");
        if (branchIds == null || !branchIds.isArray()) {
            throw new IllegalStateException(
                    "Catalog Service response must contain a branchIds array");
        }

        LinkedHashSet<Integer> uniqueIds = new LinkedHashSet<>();
        for (JsonNode branchId : branchIds) {
            if (!branchId.isIntegralNumber() || !branchId.canConvertToInt()) {
                throw new IllegalStateException(
                        "Catalog Service returned a non-integer branch ID");
            }
            uniqueIds.add(branchId.intValue());
        }
        return new ArrayList<>(uniqueIds);
    }
}

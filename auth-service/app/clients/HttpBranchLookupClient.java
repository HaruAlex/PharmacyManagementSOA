package clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import com.ecwid.consul.v1.health.model.HealthService;
import integration.consul.ConsulServiceResolver;
import integration.consul.ServiceResolver;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

@Singleton
public class HttpBranchLookupClient implements BranchLookupClient {
    private final WSClient wsClient;
    private final ServiceResolver resolver;
    private final String serviceName;
    private final String activeBranchesPath;
    private final int requestTimeoutMillis;

    @Inject
    public HttpBranchLookupClient(
            WSClient wsClient,
            Config config,
            ServiceResolver resolver) {
        this.wsClient = wsClient;
        this.resolver = resolver;
        this.serviceName = config.getString("catalog.service.name");
        this.activeBranchesPath = config.getString("catalog.service.activeBranchesPath");
        this.requestTimeoutMillis = config.getInt("catalog.service.timeout-ms");
    }

    @Override
    public CompletionStage<List<Integer>> getAllActiveBranchIds() {
        try {
            Optional<HealthService> instance = resolver.resolve(serviceName);
            if (instance.isEmpty()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("No healthy Catalog Service instance is available"));
            }
            String path = activeBranchesPath.startsWith("/")
                    ? activeBranchesPath
                    : "/" + activeBranchesPath;
            String url = ConsulServiceResolver.baseUrl(instance.get()) + path;
            return wsClient.url(url)
                    .setRequestTimeout(requestTimeoutMillis)
                    .get()
                    .thenApply(this::readActiveBranchIds);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
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
        JsonNode branches = body.get("items");
        if (branches == null || !branches.isArray()) {
            throw new IllegalStateException(
                    "Catalog Service response must contain an items array");
        }

        LinkedHashSet<Integer> uniqueIds = new LinkedHashSet<>();
        for (JsonNode branch : branches) {
            JsonNode branchId = branch.isObject() ? branch.get("id") : null;
            if (branchId == null || !branchId.isIntegralNumber() || !branchId.canConvertToInt()) {
                throw new IllegalStateException(
                        "Catalog Service returned a non-integer branch ID");
            }
            uniqueIds.add(branchId.intValue());
        }
        return new ArrayList<>(uniqueIds);
    }
}

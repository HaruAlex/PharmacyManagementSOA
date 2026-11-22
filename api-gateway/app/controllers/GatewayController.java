package controllers;

import play.mvc.*;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.catalog.model.CatalogService;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * API Gateway Controller - Forward requests tới microservices qua Consul service discovery
 * Với retry logic - nếu 1 instance fail, tự động retry với instance khác
 */
@Singleton
public class GatewayController extends Controller {
    
    private final ConsulClient consulClient;
    private final WSClient wsClient;
    private final Random random = new Random();
    
    @Inject
    public GatewayController(ConsulClient consulClient, WSClient wsClient) {
        this.consulClient = consulClient;
        this.wsClient = wsClient;
    }
    
    public CompletionStage<Result> proxyAuth(String path) {
        return proxyToService("auth-service", path);
    }
    
    public CompletionStage<Result> proxyCatalog(String path) {
        return proxyToService("catalog-service", path);
    }
    
    public CompletionStage<Result> proxyInventory(String path) {
        return proxyToService("inventory-service", path);
    }
    
    public CompletionStage<Result> proxyPurchase(String path) {
        return proxyToService("purchase-service", path);
    }
    
    public CompletionStage<Result> proxySales(String path) {
        return proxyToService("sales-service", path);
    }
    
    public CompletionStage<Result> proxyReport(String path) {
        return proxyToService("report-service", path);
    }
    
    /**
     * Forward request tới service với retry logic
     */
    private CompletionStage<Result> proxyToService(String serviceName, String path) {
        return proxyWithRetry(serviceName, path, 0);
    }
    
    /**
     * Proxy với retry - nếu instance fail, retry với instance khác
     */
    private CompletionStage<Result> proxyWithRetry(String serviceName, String path, int attempt) {
        // Max 3 attempts
        if (attempt > 2) {
            return CompletableFuture.completedFuture(
                Results.ok("{\"error\":\"All instances unavailable after 3 attempts\"}").as("application/json")
            );
        }
        
        try {
            // Query Consul mỗi lần (không cache) để có danh sách instance mới nhất
            List<CatalogService> services = consulClient.getCatalogService(serviceName, QueryParams.DEFAULT).getValue();
            
            if (services == null || services.isEmpty()) {
                return CompletableFuture.completedFuture(
                    Results.ok("{\"error\":\"Service " + serviceName + " not found in Consul\"}").as("application/json")
                );
            }
            
            // Random load balancing - chọn random instance
            CatalogService selected = services.get(random.nextInt(services.size()));
            
            // Build target URL
            String url = String.format("http://%s:%d/%s", 
                selected.getServiceAddress(), 
                selected.getServicePort(), 
                path);
            
            String selectedId = selected.getServiceId();
            
            // Forward request với timeout
            return wsClient.url(url)
                .setRequestTimeout(5000)
                .get()
                .thenApply(wsResponse -> 
                    Results.ok(wsResponse.getBody())
                        .as("application/json")
                        .withHeader("X-Served-By", selectedId)
                )
                .exceptionally(e -> {
                    // Nếu fail, retry với instance khác
                    return proxyWithRetry(serviceName, path, attempt + 1)
                        .toCompletableFuture()
                        .join();
                });
            
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                Results.ok("{\"error\":\"Gateway error: " + e.getMessage() + "\"}").as("application/json")
            );
        }
    }
}

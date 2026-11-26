package clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.ecwid.consul.v1.health.model.HealthService;
import com.typesafe.config.Config;
import integration.consul.ConsulServiceResolver;
import integration.consul.ServiceResolver;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;

/**
 * InventoryClient - HTTP Client để gọi Inventory Service
 * 
 * The Inventory instance is resolved through Consul.
 * 
 * Methods:
 * - Trừ tồn kho (deduct stock)
 * - Lấy tồn kho theo lot (get stock by lot - FEFO)
 * - Kiểm tra tồn kho đủ (check stock availability)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class InventoryClient {
    
    private final ServiceResolver resolver;
    private final String serviceName;
    private final String apiPrefix;

    @Inject
    public InventoryClient(ServiceResolver resolver, Config config) {
        this.resolver = resolver;
        this.serviceName = config.getString("inventory.service.name");
        this.apiPrefix = config.getString("inventory.service.api-prefix");
    }
    
    /**
     * Lấy tồn kho theo lô (FEFO - First Expired First Out)
     * 
     * GET /inventory/stock-by-lot?drugId=1&quantity=100&branchId=1
     */
    public String getStockByLotFefo(Long drugId, BigDecimal quantity, Long branchId) throws Exception {
        String url = inventoryServiceUrl() + "/stock-by-lot?" +
                "drugId=" + drugId +
                "&quantity=" + quantity +
                "&branchId=" + branchId;
        
        return makeHttpGet(url);
    }
    
    /**
     * Trừ tồn kho (Deduct stock)
     * 
     * POST /inventory/deduct
     */
    public String deductStock(Long drugId, Long branchId, String lotAllocationsJson) throws Exception {
        String url = inventoryServiceUrl() + "/deduct";
        
        String requestBody = "{" +
                "\"drugId\": " + drugId + "," +
                "\"branchId\": " + branchId + "," +
                "\"lotAllocations\": " + lotAllocationsJson +
                "}";
        
        return makeHttpPost(url, requestBody);
    }
    
    /**
     * Kiểm tra tồn kho đủ không
     */
    public String checkStockAvailability(Long drugId, BigDecimal quantity, Long branchId) throws Exception {
        String url = inventoryServiceUrl() + "/check-availability?" +
                "drugId=" + drugId +
                "&quantity=" + quantity +
                "&branchId=" + branchId;
        
        return makeHttpGet(url);
    }
    
    /**
     * Lấy chi tiết tồn kho của một thuốc
     */
    public String getDrugInventory(Long drugId, Long branchId) throws Exception {
        String url = inventoryServiceUrl() + "/drug/" + drugId + "/branch/" + branchId;
        return makeHttpGet(url);
    }
    
    /**
     * Hoàn trả tồn kho khi hủy hóa đơn (Restore stock from invoice)
     * 
     * POST /inventory/restore-by-invoice/{invoiceId}
     * 
     * @param branchId: Chi nhánh
     * @param invoiceId: ID hóa đơn cần hoàn trả tồn
     * @return true nếu thành công
     */
    public boolean restoreStockByInvoiceId(Long branchId, Long invoiceId) throws Exception {
        String url = inventoryServiceUrl() + "/restore-by-invoice/" + invoiceId + "?branchId=" + branchId;
        
        String response = makeHttpPost(url, "{}");
        
        // Parse response - expect {"success": true}
        // Simplified: nếu response contains "success" thì coi là OK
        return response.contains("true");
    }
    
    // ========== Helper Methods ==========

    private String inventoryServiceUrl() {
        HealthService instance = resolver.resolve(serviceName)
                .orElseThrow(() -> new IllegalStateException(
                        "No healthy Inventory Service instance is available"));
        String prefix = apiPrefix.startsWith("/") ? apiPrefix : "/" + apiPrefix;
        while (prefix.endsWith("/") && prefix.length() > 1) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return ConsulServiceResolver.baseUrl(instance) + prefix;
    }
    
    private String makeHttpGet(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("HTTP GET failed: " + responseCode);
        }
        
        return readResponse(connection);
    }
    
    private String makeHttpPost(String urlString, String requestBody) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setDoOutput(true);
        
        // Send request body
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.getBytes("utf-8");
            os.write(input, 0, input.length);
        }
        
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("HTTP POST failed: " + responseCode);
        }
        
        return readResponse(connection);
    }
    
    private String readResponse(HttpURLConnection connection) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }
}

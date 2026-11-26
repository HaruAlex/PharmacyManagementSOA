package clients;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;

/**
 * InventoryClient - HTTP Client để gọi Inventory Service
 * 
 * Base URL: http://localhost:9004/api/v1/inventory (inventory-service)
 * 
 * Methods:
 * - Trừ tồn kho (deduct stock)
 * - Lấy tồn kho theo lot (get stock by lot - FEFO)
 * - Kiểm tra tồn kho đủ (check stock availability)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class InventoryClient {
    
    private static final String INVENTORY_SERVICE_URL = "http://localhost:9004/api/v1/inventory";
    
    /**
     * Lấy tồn kho theo lô (FEFO - First Expired First Out)
     * 
     * GET /inventory/stock-by-lot?drugId=1&quantity=100&branchId=1
     */
    public String getStockByLotFefo(Long drugId, BigDecimal quantity, Long branchId) throws Exception {
        String url = INVENTORY_SERVICE_URL + "/stock-by-lot?" +
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
        String url = INVENTORY_SERVICE_URL + "/deduct";
        
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
        String url = INVENTORY_SERVICE_URL + "/check-availability?" +
                "drugId=" + drugId +
                "&quantity=" + quantity +
                "&branchId=" + branchId;
        
        return makeHttpGet(url);
    }
    
    /**
     * Lấy chi tiết tồn kho của một thuốc
     */
    public String getDrugInventory(Long drugId, Long branchId) throws Exception {
        String url = INVENTORY_SERVICE_URL + "/drug/" + drugId + "/branch/" + branchId;
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
        String url = INVENTORY_SERVICE_URL + "/restore-by-invoice/" + invoiceId + "?branchId=" + branchId;
        
        String response = makeHttpPost(url, "{}");
        
        // Parse response - expect {"success": true}
        // Simplified: nếu response contains "success" thì coi là OK
        return response.contains("true");
    }
    
    // ========== Helper Methods ==========
    
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

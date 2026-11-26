package clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.ecwid.consul.v1.health.model.HealthService;
import com.typesafe.config.Config;
import integration.consul.ConsulServiceResolver;
import integration.consul.ServiceResolver;
import models.InvoiceDetail;
import play.libs.Json;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * InventoryClient - HTTP Client để gọi Inventory Service
 * 
 * The Inventory instance is resolved through Consul.
 * 
 * Sales stock operations are handled atomically by Inventory per invoice.
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
    
    public JsonNode deductInvoiceStock(Long invoiceId, Long branchId, List<InvoiceDetail> details)
            throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode request = Json.newObject();
        request.put("invoiceId", invoiceId);
        request.put("branchId", branchId);
        com.fasterxml.jackson.databind.node.ArrayNode items = request.putArray("items");
        for (InvoiceDetail detail : details) {
            com.fasterxml.jackson.databind.node.ObjectNode item = items.addObject();
            item.put("invoiceDetailId", detail.getInvoiceDetailId());
            item.put("drugId", detail.getDrugId());
            item.put("quantityBase", detail.getQuantityBase());
        }
        String url = inventoryServiceUrl() + "/sales/deduct";
        return Json.parse(makeHttpPost(url, Json.stringify(request)));
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
        String url = inventoryServiceUrl() + "/sales/restore/" + invoiceId;
        com.fasterxml.jackson.databind.node.ObjectNode request = Json.newObject();
        request.put("branchId", branchId);
        JsonNode response = Json.parse(makeHttpPost(url, Json.stringify(request)));
        JsonNode success = response.get("success");
        return success != null && success.isBoolean() && success.booleanValue();
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
    
    private String makeHttpPost(String urlString, String requestBody) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setDoOutput(true);
        
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }
            int responseCode = connection.getResponseCode();
            InputStream responseStream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseBody = readResponse(responseStream);
            if (responseCode < 200 || responseCode >= 300) {
                throw new InventoryClientException(responseCode, responseBody);
            }
            return responseBody;
        } finally {
            connection.disconnect();
        }
    }

    private String readResponse(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
}

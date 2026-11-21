package services;

import play.Logger;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * ============================================================================
 * RESILIENT SERVICE CALLER
 * ============================================================================
 * 
 * Gọi service với retry logic, timeout, và error handling.
 * 
 * Đợt 4: Chịu lỗi (Fault Tolerance)
 * - Retry policy (GET idempotent, POST/PUT/DELETE with Idempotency-Key)
 * - Timeout: 5s connection, 10s read
 * - Exponential backoff: 100ms, 200ms
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class ResilientServiceCaller {
    
    private final ServiceDiscoveryClient serviceDiscoveryClient;
    private final RetryPolicy retryPolicy;
    
    // Timeout config (milliseconds)
    private static final int CONNECTION_TIMEOUT_MS = 5000;  // 5 seconds
    private static final int READ_TIMEOUT_MS = 10000;        // 10 seconds
    
    @Inject
    public ResilientServiceCaller(ServiceDiscoveryClient serviceDiscoveryClient) {
        this.serviceDiscoveryClient = serviceDiscoveryClient;
        this.retryPolicy = new ExponentialBackoffRetryPolicy();
    }
    
    /**
     * Gọi service với retry + timeout
     * 
     * @param serviceName Tên service (e.g., "auth-service")
     * @param method HTTP method (GET, POST, etc.)
     * @param path API path (e.g., "/user/profile")
     * @param requestHeaders Headers gởi kèm
     * @param body Request body (nếu có)
     * @return ServiceCallResult chứa status, headers, body
     */
    public ServiceCallResult callServiceWithRetry(
            String serviceName,
            String method,
            String path,
            Map<String, String> requestHeaders,
            String body
    ) {
        int attemptNumber = 0;
        Exception lastException = null;
        
        while (attemptNumber < 3) { // Tối đa 1 lần chính + 2 retry
            attemptNumber++;
            
            try {
                Logger.info("[ResilientCaller] Calling " + serviceName + " " + method + " " + path + 
                        " (attempt " + attemptNumber + ")");
                
                // Lấy instance từ Consul (round-robin)
                com.ecwid.consul.v1.health.model.HealthService healthService = 
                    serviceDiscoveryClient.getHealthyInstance(serviceName);
                
                if (healthService == null) {
                    throw new RuntimeException("No healthy instance found for " + serviceName);
                }
                
                String serviceUrl = serviceDiscoveryClient.getServiceUrl(healthService);
                String fullUrl = serviceUrl + path;
                
                Logger.debug("[ResilientCaller] Instance: " + healthService.getService().getId() + 
                        " URL: " + fullUrl);
                
                // Gọi service
                ServiceCallResult result = makeHttpCall(method, fullUrl, requestHeaders, body);
                
                if (result.isSuccess()) {
                    Logger.info("[ResilientCaller] ✓ Success (attempt " + attemptNumber + ")");
                    return result;
                } else {
                    // HTTP error (4xx, 5xx)
                    lastException = new RuntimeException("HTTP " + result.statusCode);
                    Logger.warn("[ResilientCaller] ✗ HTTP error: " + result.statusCode);
                    
                    // Không retry cho 4xx (client error)
                    if (result.statusCode >= 400 && result.statusCode < 500) {
                        return result;
                    }
                }
            } catch (Exception e) {
                lastException = e;
                Logger.error("[ResilientCaller] Exception: " + e.getMessage());
            }
            
            // Kiểm tra retry policy
            boolean idempotencyKey = requestHeaders != null && 
                    requestHeaders.containsKey("Idempotency-Key");
            
            if (retryPolicy.shouldRetry(method, idempotencyKey, attemptNumber, lastException)) {
                long backoffMs = retryPolicy.getBackoffMillis(attemptNumber);
                Logger.info("[ResilientCaller] Retrying after " + backoffMs + "ms");
                
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                Logger.warn("[ResilientCaller] No retry (policy rejected)");
                break;
            }
        }
        
        // Tất cả retry thất bại
        Logger.error("[ResilientCaller] Failed after " + attemptNumber + " attempts");
        return new ServiceCallResult(503, null, "{\"error\":\"Service unavailable after retries\"}");
    }
    
    /**
     * Thực hiện HTTP call với timeout
     */
    private ServiceCallResult makeHttpCall(
            String method,
            String urlStr,
            Map<String, String> requestHeaders,
            String body
    ) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            
            // Set timeout
            conn.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            
            // Set method
            conn.setRequestMethod(method);
            
            // Set headers
            if (requestHeaders != null) {
                for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            
            // Send body nếu có
            if (body != null && !body.isEmpty() && !method.equalsIgnoreCase("GET")) {
                conn.setDoOutput(true);
                try (var os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }
            }
            
            // Get response
            int statusCode = conn.getResponseCode();
            Map<String, java.util.List<String>> headers = conn.getHeaderFields();
            
            String responseBody = readResponse(conn);
            
            Logger.debug("[ResilientCaller] Response: " + statusCode);
            return new ServiceCallResult(statusCode, headers, responseBody);
            
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    /**
     * Đọc response body
     */
    private String readResponse(HttpURLConnection conn) throws Exception {
        try (var is = conn.getInputStream()) {
            return new String(is.readAllBytes(), "UTF-8");
        } catch (java.io.FileNotFoundException e) {
            // 4xx, 5xx response
            try (var es = conn.getErrorStream()) {
                if (es != null) {
                    return new String(es.readAllBytes(), "UTF-8");
                }
            }
            return "";
        }
    }
    
    /**
     * Kết quả gọi service
     */
    public static class ServiceCallResult {
        public int statusCode;
        public Map<String, java.util.List<String>> headers;
        public String body;
        
        public ServiceCallResult(int statusCode, Map<String, java.util.List<String>> headers, String body) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
        }
        
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
        
        public String getHeader(String name) {
            if (headers == null) return null;
            java.util.List<String> values = headers.get(name);
            return (values != null && !values.isEmpty()) ? values.get(0) : null;
        }
    }
}

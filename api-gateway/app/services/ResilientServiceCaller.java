package services;

import play.Logger;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * ============================================================================
 * RESILIENT SERVICE CALLER
 * ============================================================================
 * 
 * Gọi service với retry logic, timeout, circuit breaker, và bulkhead isolation.
 * 
 * Đợt 4: Chịu lỗi (Fault Tolerance)
 * Đợt 5: Resilience (Circuit Breaker + Bulkhead)
 * - Retry policy (GET idempotent, POST/PUT/DELETE with Idempotency-Key)
 * - Timeout: 5s connection, 10s read
 * - Exponential backoff: 100ms, 200ms
 * - Circuit Breaker: fail fast khi service chết
 * - Bulkhead: thread pool isolation, reject nếu queue full
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class ResilientServiceCaller {
    
    private final ServiceDiscoveryClient serviceDiscoveryClient;
    private final RetryPolicy retryPolicy;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    
    // Timeout config (milliseconds)
    private static final int CONNECTION_TIMEOUT_MS = 5000;  // 5 seconds
    private static final int READ_TIMEOUT_MS = 10000;        // 10 seconds
    private static final long BULKHEAD_TIMEOUT_MS = 5000;    // 5 seconds
    
    @Inject
    public ResilientServiceCaller(
            ServiceDiscoveryClient serviceDiscoveryClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            BulkheadRegistry bulkheadRegistry
    ) {
        this.serviceDiscoveryClient = serviceDiscoveryClient;
        this.retryPolicy = new ExponentialBackoffRetryPolicy();
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
    }
    
    /**
     * Gọi service với retry + timeout + circuit breaker + bulkhead
     */
    public ServiceCallResult callServiceWithRetry(
            String serviceName,
            String method,
            String path,
            Map<String, String> requestHeaders,
            String body
    ) {
        // Get circuit breaker for this service
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.getOrCreate(serviceName);
        
        // Fail fast nếu circuit breaker OPEN
        if (!circuitBreaker.isCallAllowed()) {
            Logger.warn("[ResilientCaller] Circuit breaker OPEN for " + serviceName + " - rejecting call");
            return new ServiceCallResult(503, null, "{\"error\":\"Service temporarily unavailable (circuit open)\"}");
        }
        
        // Get bulkhead executor for this service
        BulkheadExecutor bulkhead = bulkheadRegistry.getOrCreate(serviceName);
        
        // Retry logic
        int attemptNumber = 0;
        Exception lastException = null;
        
        while (attemptNumber < 3) {  // 1 main + 2 retries
            attemptNumber++;
            
            try {
                Logger.info("[ResilientCaller] Calling " + serviceName + " " + method + " " + path + 
                        " (attempt " + attemptNumber + ")");
                
                // Execute call in bulkhead (thread pool + queue timeout)
                try {
                    ServiceCallResult result = bulkhead.execute(
                        new Callable<ServiceCallResult>() {
                            @Override
                            public ServiceCallResult call() throws Exception {
                                return makeHttpCallInternal(serviceName, method, path, requestHeaders, body);
                            }
                        },
                        BULKHEAD_TIMEOUT_MS
                    );
                    
                    if (result.isSuccess()) {
                        circuitBreaker.recordSuccess();
                        Logger.info("[ResilientCaller] ✓ Success (attempt " + attemptNumber + ")");
                        return result;
                    } else {
                        lastException = new RuntimeException("HTTP " + result.statusCode);
                        Logger.warn("[ResilientCaller] ✗ HTTP error: " + result.statusCode);
                        
                        // Don't retry for 4xx (client error)
                        if (result.statusCode >= 400 && result.statusCode < 500) {
                            circuitBreaker.recordFailure();
                            return result;
                        }
                    }
                } catch (Exception e) {
                    lastException = e;
                    Logger.error("[ResilientCaller] Bulkhead exception: " + e.getMessage());
                    
                    // Check if bulkhead-related error
                    if (e.getMessage() != null && e.getMessage().contains("Bulkhead")) {
                        Logger.error("[ResilientCaller] Bulkhead error - returning 503");
                        return new ServiceCallResult(503, null, "{\"error\":\"" + e.getMessage() + "\"}");
                    }
                }
                
            } catch (RuntimeException e) {
                lastException = e;
                Logger.error("[ResilientCaller] Runtime exception: " + e.getMessage());
            }

            
            // Check retry policy
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
        
        // All retries failed
        circuitBreaker.recordFailure();
        Logger.error("[ResilientCaller] Failed after " + attemptNumber + " attempts");
        return new ServiceCallResult(503, null, "{\"error\":\"Service unavailable after retries\"}");
    }
    
    /**
     * Actual HTTP call (runs in bulkhead)
     */
    private ServiceCallResult makeHttpCallInternal(
            String serviceName,
            String method,
            String path,
            Map<String, String> requestHeaders,
            String body
    ) throws Exception {
        // Get instance from discovery
        com.ecwid.consul.v1.health.model.HealthService healthService = 
            serviceDiscoveryClient.getHealthyInstance(serviceName);
        
        if (healthService == null) {
            throw new RuntimeException("No healthy instance found for " + serviceName);
        }
        
        String serviceUrl = serviceDiscoveryClient.getServiceUrl(healthService);
        String fullUrl = serviceUrl + path;
        
        Logger.debug("[ResilientCaller] Instance: " + healthService.getService().getId() + 
                " URL: " + fullUrl);
        
        return makeHttpCall(method, fullUrl, requestHeaders, body);
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


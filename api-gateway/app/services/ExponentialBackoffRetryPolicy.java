package services;

import play.Logger;

/**
 * ============================================================================
 * EXPONENTIAL BACKOFF RETRY POLICY
 * ============================================================================
 * 
 * Retry strategy với exponential backoff:
 * - GET request: retry 2 lần (vì idempotent)
 * - POST/PUT/DELETE có Idempotency-Key: retry 2 lần
 * - Các request khác: không retry
 * - Backoff: 100ms, 200ms (exponential: base^attempt * 100ms)
 * 
 * Đợt 4: Chịu lỗi (Fault Tolerance)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class ExponentialBackoffRetryPolicy implements RetryPolicy {
    
    private static final int MAX_RETRIES = 2;
    private static final long BASE_BACKOFF_MS = 100;
    
    @Override
    public boolean shouldRetry(String method, boolean hasIdempotencyKey, int attemptNumber, Exception exception) {
        // Không retry nếu đã vượt quá số lần tối đa
        if (attemptNumber > MAX_RETRIES) {
            return false;
        }
        
        // GET request luôn được retry (idempotent)
        if ("GET".equalsIgnoreCase(method)) {
            Logger.info("[RetryPolicy] GET request (attempt " + attemptNumber + ") - will retry");
            return true;
        }
        
        // POST/PUT/DELETE/PATCH có Idempotency-Key được retry
        if (hasIdempotencyKey && 
            ("POST".equalsIgnoreCase(method) || 
             "PUT".equalsIgnoreCase(method) || 
             "DELETE".equalsIgnoreCase(method) || 
             "PATCH".equalsIgnoreCase(method))) {
            Logger.info("[RetryPolicy] " + method + " with Idempotency-Key (attempt " + attemptNumber + ") - will retry");
            return true;
        }
        
        // Các trường hợp khác: không retry
        Logger.warn("[RetryPolicy] " + method + " (attempt " + attemptNumber + ") - no retry");
        return false;
    }
    
    @Override
    public long getBackoffMillis(int attemptNumber) {
        // Exponential backoff: 100ms * 2^(attempt-1)
        // Attempt 1: 100ms
        // Attempt 2: 200ms
        long backoff = BASE_BACKOFF_MS * (long) Math.pow(2, attemptNumber - 1);
        Logger.debug("[RetryPolicy] Backoff for attempt " + attemptNumber + ": " + backoff + "ms");
        return backoff;
    }
}

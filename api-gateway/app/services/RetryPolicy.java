package services;

/**
 * ============================================================================
 * RETRY POLICY INTERFACE
 * ============================================================================
 * 
 * Định nghĩa strategy retry cho các request thất bại.
 * Đợt 4: Chịu lỗi (Fault Tolerance)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public interface RetryPolicy {
    
    /**
     * Quyết định có nên retry hay không
     * 
     * @param method HTTP method (GET, POST, PUT, DELETE, PATCH)
     * @param hasIdempotencyKey true nếu request có Idempotency-Key header
     * @param attemptNumber attempt thứ bao nhiêu (1, 2, 3...)
     * @param exception exception nếu có (null nếu không fail)
     * @return true = nên retry, false = dừng
     */
    boolean shouldRetry(String method, boolean hasIdempotencyKey, int attemptNumber, Exception exception);
    
    /**
     * Tính thời gian chờ trước khi retry (exponential backoff)
     * 
     * @param attemptNumber attempt thứ bao nhiêu (1, 2, 3...)
     * @return milliseconds để chờ
     */
    long getBackoffMillis(int attemptNumber);
}

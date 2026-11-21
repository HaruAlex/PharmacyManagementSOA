package services;

/**
 * ============================================================================
 * CIRCUIT BREAKER INTERFACE
 * ============================================================================
 * 
 * Định nghĩa circuit breaker pattern:
 * - CLOSED: normal operation
 * - OPEN: fail fast, reject requests
 * - HALF_OPEN: test probe request
 * 
 * Đợt 5: Resilience (Circuit Breaker)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public interface CircuitBreaker {
    
    /**
     * Các state của circuit breaker
     */
    enum State {
        CLOSED,       // Normal: accept requests
        OPEN,         // Fail fast: reject requests immediately
        HALF_OPEN     // Testing: try 1 request
    }
    
    /**
     * Lấy state hiện tại
     */
    State getState();
    
    /**
     * Ghi lại success
     */
    void recordSuccess();
    
    /**
     * Ghi lại failure
     */
    void recordFailure();
    
    /**
     * Kiểm tra có nên thực hiện call hay không (dùng trong OPEN state)
     * 
     * @return true = được phép call, false = bị reject (fail fast)
     */
    boolean isCallAllowed();
    
    /**
     * Reset metrics (khi chuyển sang HALF_OPEN để test)
     */
    void reset();
    
    /**
     * Get circuit breaker name (for logging)
     */
    String getName();
    
    /**
     * Get failure count (for metrics)
     */
    int getFailureCount();
    
    /**
     * Get success count (for metrics)
     */
    int getSuccessCount();
}

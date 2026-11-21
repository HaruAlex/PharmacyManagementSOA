package services;

import play.Logger;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ============================================================================
 * SIMPLE CIRCUIT BREAKER IMPLEMENTATION
 * ============================================================================
 * 
 * State machine: CLOSED → OPEN → HALF_OPEN → CLOSED
 * 
 * Metrics:
 * - Failure threshold: 5 failures
 * - Success threshold: 2 successes (in HALF_OPEN)
 * - Timeout: 30 seconds (OPEN → HALF_OPEN)
 * - Window: 10 requests (sliding window)
 * 
 * Đợt 5: Resilience (Circuit Breaker)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class SimpleCircuitBreaker implements CircuitBreaker {
    
    private final String name;
    private final int failureThreshold;  // 5
    private final int successThreshold;  // 2
    private final long timeoutMs;        // 30000ms = 30s
    private final int windowSize;        // 10
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong openedAt = new AtomicLong(0);
    
    public SimpleCircuitBreaker(String name) {
        this.name = name;
        this.failureThreshold = 5;
        this.successThreshold = 2;
        this.timeoutMs = 30000;  // 30 seconds
        this.windowSize = 10;
    }
    
    @Override
    public State getState() {
        State current = state.get();
        
        // Check if should transition from OPEN to HALF_OPEN
        if (current == State.OPEN) {
            long now = System.currentTimeMillis();
            long openDuration = now - openedAt.get();
            
            if (openDuration >= timeoutMs) {
                Logger.info("[CircuitBreaker] " + name + " transitioning OPEN → HALF_OPEN");
                state.set(State.HALF_OPEN);
                reset();
                return State.HALF_OPEN;
            }
        }
        
        return current;
    }
    
    @Override
    public void recordSuccess() {
        State current = getState();
        Logger.debug("[CircuitBreaker] " + name + " recordSuccess (state=" + current + ")");
        
        if (current == State.CLOSED) {
            // Normal operation - keep circuit closed
            failureCount.set(0);
            requestCount.incrementAndGet();
            
            // Reset if window exceeded
            if (requestCount.get() > windowSize) {
                requestCount.set(0);
            }
            
            Logger.debug("[CircuitBreaker] " + name + " CLOSED: success recorded");
            
        } else if (current == State.HALF_OPEN) {
            // Testing phase - try to close circuit
            successCount.incrementAndGet();
            
            if (successCount.get() >= successThreshold) {
                Logger.info("[CircuitBreaker] " + name + " transitioning HALF_OPEN → CLOSED ✅");
                state.set(State.CLOSED);
                reset();
            }
        }
    }
    
    @Override
    public void recordFailure() {
        State current = getState();
        Logger.debug("[CircuitBreaker] " + name + " recordFailure (state=" + current + ")");
        
        lastFailureTime.set(System.currentTimeMillis());
        
        if (current == State.CLOSED) {
            // Normal operation - count failures
            failureCount.incrementAndGet();
            requestCount.incrementAndGet();
            
            // Reset if window exceeded
            if (requestCount.get() > windowSize) {
                failureCount.set(1);
                requestCount.set(1);
            }
            
            int failures = failureCount.get();
            Logger.warn("[CircuitBreaker] " + name + " CLOSED: failures = " + failures + "/" + failureThreshold);
            
            // Open if threshold exceeded
            if (failures >= failureThreshold) {
                Logger.error("[CircuitBreaker] " + name + " transitioning CLOSED → OPEN 🔴 (failures=" + failures + ")");
                state.set(State.OPEN);
                openedAt.set(System.currentTimeMillis());
            }
            
        } else if (current == State.HALF_OPEN) {
            // Testing phase - reopen immediately
            Logger.warn("[CircuitBreaker] " + name + " transitioning HALF_OPEN → OPEN 🔴 (test failed)");
            state.set(State.OPEN);
            openedAt.set(System.currentTimeMillis());
            reset();
        }
    }
    
    @Override
    public boolean isCallAllowed() {
        State current = getState();
        
        if (current == State.CLOSED || current == State.HALF_OPEN) {
            return true;  // Allow call
        }
        
        // OPEN: reject immediately (fail fast)
        Logger.warn("[CircuitBreaker] " + name + " rejecting call (OPEN state - fail fast)");
        return false;
    }
    
    @Override
    public void reset() {
        failureCount.set(0);
        successCount.set(0);
        requestCount.set(0);
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * Get metrics for monitoring
     */
    public CircuitBreakerMetrics getMetrics() {
        return new CircuitBreakerMetrics(
            name,
            getState(),
            failureCount.get(),
            successCount.get(),
            requestCount.get(),
            openedAt.get()
        );
    }
    
    /**
     * Metrics DTO
     */
    public static class CircuitBreakerMetrics {
        public String name;
        public State state;
        public int failureCount;
        public int successCount;
        public int requestCount;
        public long openedAtMs;
        
        public CircuitBreakerMetrics(String name, State state, int failureCount, 
                                      int successCount, int requestCount, long openedAtMs) {
            this.name = name;
            this.state = state;
            this.failureCount = failureCount;
            this.successCount = successCount;
            this.requestCount = requestCount;
            this.openedAtMs = openedAtMs;
        }
        
        @Override
        public String toString() {
            return name + " [" + state + "] failures=" + failureCount + 
                   " successes=" + successCount + " window=" + requestCount;
        }
    }
}

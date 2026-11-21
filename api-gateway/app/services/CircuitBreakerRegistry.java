package services;

import play.Logger;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 * CIRCUIT BREAKER REGISTRY
 * ============================================================================
 * 
 * Quản lý 1 circuit breaker cho mỗi service.
 * Singleton để tất cả request chia sẻ cùng metrics.
 * 
 * Đợt 5: Resilience (Circuit Breaker)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class CircuitBreakerRegistry {
    
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    
    /**
     * Get all circuit breakers for metrics collection
     */
    public Map<String, CircuitBreaker> getAllCircuitBreakers() {
        return new ConcurrentHashMap<>(breakers);
    }
    
    /**
     * Lấy hoặc tạo circuit breaker cho service
     * 
     * @param serviceName Tên service (e.g., "auth-service")
     * @return CircuitBreaker instance
     */
    public CircuitBreaker getOrCreate(String serviceName) {
        return breakers.computeIfAbsent(serviceName, name -> {
            Logger.info("[CircuitBreakerRegistry] Creating circuit breaker for: " + name);
            return new SimpleCircuitBreaker(name);
        });
    }
    
    /**
     * Reset circuit breaker (for testing)
     */
    public void reset(String serviceName) {
        CircuitBreaker breaker = breakers.get(serviceName);
        if (breaker != null) {
            breaker.reset();
            Logger.info("[CircuitBreakerRegistry] Reset circuit breaker: " + serviceName);
        }
    }
    
    /**
     * Get all metrics
     */
    public void logMetrics() {
        breakers.forEach((name, breaker) -> {
            if (breaker instanceof SimpleCircuitBreaker) {
                SimpleCircuitBreaker simple = (SimpleCircuitBreaker) breaker;
                Logger.info("[CircuitBreakerRegistry] " + simple.getMetrics());
            }
        });
    }
    
    /**
     * Get circuit breaker state
     */
    public String getState(String serviceName) {
        CircuitBreaker breaker = breakers.get(serviceName);
        if (breaker != null) {
            return breaker.getState().toString();
        }
        return "NOT_FOUND";
    }
}

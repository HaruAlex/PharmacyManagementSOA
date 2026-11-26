package services;

import com.google.inject.AbstractModule;

import javax.inject.Singleton;

/**
 * ============================================================================
 * GATEWAY MODULE - IDEMPOTENCY + RESILIENCE
 * ============================================================================
 * 
 * Consul registration and discovery are provided by the shared integration module.
 * 
 * Đợt 4: Thêm IdempotencyCache
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class GatewayModule extends AbstractModule {

    @Override
    protected void configure() {
        // Đợt 4: Bind IdempotencyCache
        bind(IdempotencyCache.class).to(InMemoryIdempotencyCache.class).in(Singleton.class);
        
        // Đợt 5: Bind Circuit Breaker + Bulkhead registries
        bind(CircuitBreakerRegistry.class).in(Singleton.class);
        bind(BulkheadRegistry.class).in(Singleton.class);
    }
}

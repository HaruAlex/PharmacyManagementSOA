package services;

import com.ecwid.consul.v1.ConsulClient;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.typesafe.config.Config;

import javax.inject.Singleton;

/**
 * ============================================================================
 * GATEWAY MODULE - CONSUL CLIENT + LOAD BALANCER + IDEMPOTENCY
 * ============================================================================
 * 
 * API Gateway cần ConsulClient để discover service, không đăng ký.
 * 
 * Đợt 2: Khám phá dịch vụ (Service Discovery)
 * Đợt 3: Thêm LoadBalancer (Round-robin)
 * Đợt 4: Thêm IdempotencyCache
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class GatewayModule extends AbstractModule {

    @Override
    protected void configure() {
        // Đợt 3: Bind LoadBalancer implementation (Round-robin)
        bind(LoadBalancer.class).to(RoundRobinLoadBalancer.class).in(Singleton.class);
        
        // Đợt 4: Bind IdempotencyCache
        bind(IdempotencyCache.class).to(InMemoryIdempotencyCache.class).in(Singleton.class);
        
        // Đợt 5: Bind Circuit Breaker + Bulkhead registries
        bind(CircuitBreakerRegistry.class).in(Singleton.class);
        bind(BulkheadRegistry.class).in(Singleton.class);
    }

    /**
     * Tạo ConsulClient singleton cho API Gateway
     * Gateway chỉ dùng Consul để discover service, không đăng ký
     */
    @Provides
    @Singleton
    public ConsulClient consulClient(Config config) {
        String consulHost = config.hasPath("consul.host") ? config.getString("consul.host") : "localhost";
        int consulPort = config.hasPath("consul.port") ? config.getInt("consul.port") : 8500;
        return new ConsulClient(consulHost, consulPort);
    }
}

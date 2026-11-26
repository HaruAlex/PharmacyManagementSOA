package services;

import com.ecwid.consul.v1.health.model.HealthService;
import integration.consul.ConsulServiceResolver;
import integration.consul.ServiceResolver;
import play.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * ============================================================================
 * SERVICE DISCOVERY CLIENT
 * ============================================================================
 * 
 * API Gateway sử dụng Consul để khám phá dịch vụ thay vì hard-code URL.
 * 
 * Đợt 2: Khám phá dịch vụ (Service Discovery)
 * Round-robin and healthy-instance selection are provided by the shared resolver.
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class ServiceDiscoveryClient {
    
    private final ServiceResolver resolver;
    
    @Inject
    public ServiceDiscoveryClient(ServiceResolver resolver) {
        this.resolver = resolver;
    }
    
    /**
     * Lấy danh sách instance của một service từ Consul (chỉ passing)
     * 
     * @param serviceName Tên service
     * @return Danh sách HealthService
     */
    public List<HealthService> getHealthyServiceInstances(String serviceName) {
        List<HealthService> services = resolver.getHealthyInstances(serviceName);
        Logger.info("[ServiceDiscovery] " + serviceName + ": " + services.size() + " passing instances");
        return services;
    }
    
    /**
     * Select a healthy instance through the shared resolver.
     */
    public HealthService getHealthyInstance(String serviceName) {
        return resolver.resolve(serviceName).orElse(null);
    }
    
    /**
     * Lấy URL của service
     */
    public String getServiceUrl(HealthService healthService) {
        return healthService == null ? null : ConsulServiceResolver.baseUrl(healthService);
    }
}

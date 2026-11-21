package services;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.health.model.HealthService;
import play.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ============================================================================
 * SERVICE DISCOVERY CLIENT
 * ============================================================================
 * 
 * API Gateway sử dụng Consul để khám phá dịch vụ thay vì hard-code URL.
 * 
 * Đợt 2: Khám phá dịch vụ (Service Discovery)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class ServiceDiscoveryClient {
    
    private final ConsulClient consulClient;
    private final Random random = new Random();
    
    @Inject
    public ServiceDiscoveryClient(ConsulClient consulClient) {
        this.consulClient = consulClient;
    }
    
    /**
     * Lấy danh sách instance của một service từ Consul (chỉ passing)
     * 
     * @param serviceName Tên service
     * @return Danh sách HealthService
     */
    public List<HealthService> getHealthyServiceInstances(String serviceName) {
        try {
            // Gọi Consul: lấy tất cả instance của service (onlyPassing=true)
            // Parameter: serviceName, tag (null), onlyPassing (true), queryParams (null)
            List<HealthService> services = consulClient.getHealthServices(serviceName, true, null).getValue();
            if (services == null) {
                services = new ArrayList<>();
            }
            
            Logger.info("[ServiceDiscovery] " + serviceName + ": " + services.size() + " instance passing");
            return services;
        } catch (Exception e) {
            Logger.error("[ServiceDiscovery] Lỗi khi lấy " + serviceName + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Chọn ngẫu nhiên 1 instance khỏe mạnh
     */
    public HealthService getRandomHealthyInstance(String serviceName) {
        List<HealthService> instances = getHealthyServiceInstances(serviceName);
        if (instances.isEmpty()) {
            return null;
        }
        return instances.get(random.nextInt(instances.size()));
    }
    
    /**
     * Lấy URL của service
     */
    public String getServiceUrl(HealthService healthService) {
        if (healthService == null) return null;
        String host = healthService.getService().getAddress();
        int port = healthService.getService().getPort();
        return "http://" + host + ":" + port;
    }
}

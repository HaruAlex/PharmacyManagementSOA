package modules;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewService;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import play.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.net.InetAddress;

/**
 * ============================================================================
 * CONSUL MODULE - ĐĂNG KÝ/HỦY ĐĂNG KÝ SERVICE TỰ ĐỘNG
 * ============================================================================
 * 
 * Module này tự động đăng ký service lên Consul khi khởi động
 * và hủy đăng ký khi tắt.
 * 
 * Đợt 2: Khám phá dịch vụ (Service Discovery)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class ConsulModule extends AbstractModule {

    /**
     * Cấu hình module: bind ConsulClient singleton
     */
    @Override
    protected void configure() {
        // Không cần bind gì, dùng @Provides method bên dưới
    }

    /**
     * Tạo ConsulClient singleton và đăng ký service
     * 
     * @param consulHost Consul host (mặc định localhost)
     * @param consulPort Consul port (mặc định 8500)
     * @param serviceName Tên service (từ application.conf)
     * @param serviceHost Host service (lấy từ InetAddress)
     * @param servicePort Cổng service (từ application.conf)
     * @param instanceId Instance ID (từ system property)
     * @return ConsulClient đã cấu hình và đăng ký service
     */
    @Provides
    @Singleton
    public ConsulClient consulClient(
            @Named("consul.host") String consulHost,
            @Named("consul.port") int consulPort,
            @Named("service.name") String serviceName,
            @Named("service.host") String serviceHost,
            @Named("service.port") int servicePort,
            @Named("service.instance.id") String instanceId
    ) {
        // Tạo Consul client
        ConsulClient consulClient = new ConsulClient(consulHost, consulPort);
        
        try {
            // Tạo NewService object để đăng ký
            NewService newService = new NewService();
            newService.setId(instanceId); // Service ID duy nhất (vd: auth-1, auth-2)
            newService.setName(serviceName); // Service Name (vd: auth-service)
            newService.setAddress(serviceHost);
            newService.setPort(servicePort);
            
            // Cấu hình health check
            // Consul sẽ gọi /health mỗi 10 giây, timeout 3 giây
            NewService.Check healthCheck = new NewService.Check();
            healthCheck.setHttp("http://" + serviceHost + ":" + servicePort + "/health");
            healthCheck.setInterval("10s");
            healthCheck.setTimeout("3s");
            healthCheck.setDeregisterCriticalServiceAfter("1m");
            
            newService.setCheck(healthCheck);
            
            // Đăng ký service lên Consul
            consulClient.agentServiceRegister(newService);
            
            Logger.info("✓ [" + instanceId + "] Đã đăng ký lên Consul: " + serviceName + 
                    " (" + serviceHost + ":" + servicePort + ")");
            
        } catch (Exception e) {
            Logger.error("✗ [" + instanceId + "] Lỗi khi đăng ký Consul: " + e.getMessage(), e);
        }
        
        return consulClient;
    }

    /**
     * Hook hủy đăng ký service khi application dừng
     * 
     * @param consulClient ConsulClient
     * @param instanceId Instance ID
     */
    @Inject
    public void onApplicationStop(ConsulClient consulClient, @Named("service.instance.id") String instanceId) {
        // Play sẽ tự gọi hook này khi application dừng
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                consulClient.agentServiceDeregister(instanceId);
                Logger.info("✓ [" + instanceId + "] Đã hủy đăng ký khỏi Consul");
            } catch (Exception e) {
                Logger.error("✗ [" + instanceId + "] Lỗi hủy đăng ký Consul: " + e.getMessage());
            }
        }));
    }
}

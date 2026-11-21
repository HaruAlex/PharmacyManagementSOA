package modules;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewService;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.typesafe.config.Config;
import play.Logger;

import javax.inject.Inject;
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
     * @param config Typesafe Config
     * @return ConsulClient đã cấu hình và đăng ký service
     */
    @Provides
    @Singleton
    public ConsulClient consulClient(Config config) {
        // Lấy config từ application.conf
        String consulHost = config.hasPath("consul.host") ? config.getString("consul.host") : "localhost";
        int consulPort = config.hasPath("consul.port") ? config.getInt("consul.port") : 8500;
        String serviceName = config.hasPath("service.name") ? config.getString("service.name") : "unknown-service";
        String serviceHost = config.hasPath("service.host") ? config.getString("service.host") : "localhost";
        int servicePort = config.hasPath("service.port") ? config.getInt("service.port") : 9001;
        String instanceId = config.hasPath("service.instance.id") ? config.getString("service.instance.id") : "unknown-instance";
        
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
     * @param config Typesafe Config
     */
    @Inject
    public void onApplicationStop(ConsulClient consulClient, Config config) {
        String instanceId = config.hasPath("service.instance.id") ? config.getString("service.instance.id") : "unknown-instance";
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

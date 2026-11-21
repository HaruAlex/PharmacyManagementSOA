package modules;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewService;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import play.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * ============================================================================
 * CONSUL MODULE - ĐĂNG KÝ/HỦY ĐĂNG KÝ SERVICE TỰ ĐỘNG
 * ============================================================================
 * 
 * Module này tự động đăng ký catalog-service lên Consul khi khởi động
 * và hủy đăng ký khi tắt.
 * 
 * Đợt 2: Khám phá dịch vụ (Service Discovery)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class ConsulModule extends AbstractModule {

    @Override
    protected void configure() {
        // Không cần bind gì, dùng @Provides method bên dưới
    }

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
        ConsulClient consulClient = new ConsulClient(consulHost, consulPort);
        
        try {
            NewService newService = new NewService();
            newService.setId(instanceId);
            newService.setName(serviceName);
            newService.setAddress(serviceHost);
            newService.setPort(servicePort);
            
            NewService.Check healthCheck = new NewService.Check();
            healthCheck.setHttp("http://" + serviceHost + ":" + servicePort + "/health");
            healthCheck.setInterval("10s");
            healthCheck.setTimeout("3s");
            healthCheck.setDeregisterCriticalServiceAfter("1m");
            
            newService.setCheck(healthCheck);
            consulClient.agentServiceRegister(newService);
            
            Logger.info("✓ [" + instanceId + "] Đã đăng ký lên Consul: " + serviceName + 
                    " (" + serviceHost + ":" + servicePort + ")");
            
        } catch (Exception e) {
            Logger.error("✗ [" + instanceId + "] Lỗi khi đăng ký Consul: " + e.getMessage(), e);
        }
        
        return consulClient;
    }

    @Inject
    public void onApplicationStop(ConsulClient consulClient, @Named("service.instance.id") String instanceId) {
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

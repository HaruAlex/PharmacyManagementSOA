package services;

import com.ecwid.consul.v1.ConsulClient;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * ============================================================================
 * GATEWAY MODULE - CONSUL CLIENT CHO API GATEWAY
 * ============================================================================
 * 
 * API Gateway cần ConsulClient để discover service, nhưng KHÔNG đăng ký lên Consul.
 * 
 * Đợt 2: Khám phá dịch vụ (Service Discovery)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class GatewayModule extends AbstractModule {

    @Override
    protected void configure() {
        // Không cần bind gì, dùng @Provides method bên dưới
    }

    /**
     * Tạo ConsulClient singleton cho API Gateway
     * Gateway chỉ dùng Consul để discover service, không đăng ký
     */
    @Provides
    @Singleton
    public ConsulClient consulClient(
            @Named("consul.host") String consulHost,
            @Named("consul.port") int consulPort
    ) {
        return new ConsulClient(consulHost, consulPort);
    }
}

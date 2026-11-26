package modules;

import com.google.inject.AbstractModule;
import play.Logger;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import com.ecwid.consul.v1.ConsulClient;
import controllers.HealthController;

public class StartupModule extends AbstractModule {
    @Override
    protected void configure() {
        // Khởi tạo sớm để HealthController được tạo khi ứng dụng bắt đầu
        bind(ServiceInitializer.class).asEagerSingleton();
    }
    
    @Singleton
    public static class ServiceInitializer {
        @Inject
        public ServiceInitializer(ConsulClient consulClient, HealthController healthController) {
            // Buộc khởi tạo cả hai thành phần
            Logger.info("[ServiceInitializer] ConsulModule + HealthController initialized");
        }
    }
}

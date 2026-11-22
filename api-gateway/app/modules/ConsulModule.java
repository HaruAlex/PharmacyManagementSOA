package modules;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewService;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.typesafe.config.Config;
import play.Logger;

import javax.inject.Singleton;

public class ConsulModule extends AbstractModule {

    @Override
    protected void configure() {
        // Bindings are provided via @Provides methods using Typesafe Config
    }

    @Provides
    @Singleton
    public ConsulClient consulClient(Config config) {
        String consulHost = config.hasPath("consul.host") ? config.getString("consul.host") : "localhost";
        int consulPort = config.hasPath("consul.port") ? config.getInt("consul.port") : 8500;

        String serviceName = config.hasPath("service.name") ? config.getString("service.name") : "api-gateway";
        String serviceHost = config.hasPath("service.host") ? config.getString("service.host") : "localhost";
        int servicePort = config.hasPath("service.port") ? config.getInt("service.port") : 9000;
        String instanceId = config.hasPath("service.instance.id")
                ? config.getString("service.instance.id")
                : (serviceName + "-" + servicePort);

        ConsulClient consulClient = new ConsulClient(consulHost, consulPort);

        try {
            // Gateway không đăng ký lên Consul (chỉ dùng để discover service)
            // Nếu cần đăng ký, bỏ comment dòng dưới
            // NewService newService = new NewService();
            // newService.setId(instanceId);
            // newService.setName(serviceName);
            // newService.setAddress(serviceHost);
            // newService.setPort(servicePort);
            //
            // NewService.Check healthCheck = new NewService.Check();
            // healthCheck.setHttp("http://" + serviceHost + ":" + servicePort + "/health");
            // healthCheck.setInterval("10s");
            // healthCheck.setTimeout("3s");
            // healthCheck.setDeregisterCriticalServiceAfter("1m");
            //
            // newService.setCheck(healthCheck);
            // consulClient.agentServiceRegister(newService);

            Logger.info("[" + instanceId + "] Connected to Consul at " + consulHost + ":" + consulPort);
        } catch (Exception e) {
            Logger.error("[" + instanceId + "] Failed to connect to Consul: " + e.getMessage(), e);
        }

        return consulClient;
    }

    public void onApplicationStop(ConsulClient consulClient, Config config) {
        String serviceName = config.hasPath("service.name") ? config.getString("service.name") : "api-gateway";
        int servicePort = config.hasPath("service.port") ? config.getInt("service.port") : 9000;
        String instanceId = config.hasPath("service.instance.id")
                ? config.getString("service.instance.id")
                : (serviceName + "-" + servicePort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // Gateway không đăng ký nên không cần deregister
                Logger.info("[" + instanceId + "] Disconnecting from Consul");
            } catch (Exception e) {
                Logger.error("[" + instanceId + "] Error during shutdown: " + e.getMessage());
            }
        }));
    }
}

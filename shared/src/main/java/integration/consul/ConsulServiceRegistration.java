package integration.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewService;
import com.typesafe.config.Config;
import play.Logger;
import play.inject.ApplicationLifecycle;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;

@Singleton
public final class ConsulServiceRegistration {
    private final ConsulClient consulClient;
    private final String instanceId;

    @Inject
    public ConsulServiceRegistration(
            Config config,
            ConsulClient consulClient,
            ApplicationLifecycle lifecycle) {
        String serviceName = requiredString(config, "service.name");
        int port = config.hasPath("service.port")
                ? config.getInt("service.port")
                : config.getInt("play.server.http.port");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("service.port must be between 1 and 65535");
        }

        String address = serviceAddress(config);
        this.instanceId = instanceId(config, serviceName, address, port);
        this.consulClient = consulClient;

        String healthPath = config.hasPath("service.health-path")
                ? config.getString("service.health-path")
                : "/health";
        if (!healthPath.startsWith("/") || healthPath.contains("?")) {
            throw new IllegalArgumentException("service.health-path must be an absolute path without a query");
        }

        NewService service = new NewService();
        service.setId(instanceId);
        service.setName(serviceName);
        service.setAddress(address);
        service.setPort(port);

        NewService.Check check = new NewService.Check();
        check.setHttp("http://" + address + ":" + port + healthPath);
        check.setInterval(config.getString("consul.health.interval"));
        check.setTimeout(config.getString("consul.health.timeout"));
        check.setDeregisterCriticalServiceAfter(config.getString("consul.health.deregister-after"));
        service.setCheck(check);

        try {
            consulClient.agentServiceRegister(service);
            Logger.info("[" + instanceId + "] Registered with Consul: " + serviceName
                    + " (" + address + ":" + port + ")");
        } catch (RuntimeException e) {
            Logger.error("[" + instanceId + "] Failed to register with Consul", e);
            throw new IllegalStateException("Could not register " + serviceName + " with Consul", e);
        }

        lifecycle.addStopHook(() -> {
            try {
                consulClient.agentServiceDeregister(instanceId);
                Logger.info("[" + instanceId + "] Deregistered from Consul");
                return CompletableFuture.completedFuture(null);
            } catch (RuntimeException e) {
                Logger.error("[" + instanceId + "] Failed to deregister from Consul", e);
                return CompletableFuture.failedFuture(e);
            }
        });
    }

    public String getInstanceId() {
        return instanceId;
    }

    public static String instanceId(Config config) {
        String serviceName = requiredString(config, "service.name");
        int port = config.hasPath("service.port")
                ? config.getInt("service.port")
                : config.getInt("play.server.http.port");
        return instanceId(config, serviceName, serviceAddress(config), port);
    }

    private static String instanceId(Config config, String serviceName, String address, int port) {
        return config.hasPath("service.instance.id")
                ? requiredString(config, "service.instance.id")
                : serviceName + "-" + address + "-" + port;
    }

    private static String serviceAddress(Config config) {
        if (config.hasPath("service.address")) {
            return requiredString(config, "service.address");
        }
        if (config.hasPath("service.host")) {
            return requiredString(config, "service.host");
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Could not determine service hostname; set SERVICE_ADDRESS", e);
        }
    }

    private static String requiredString(Config config, String path) {
        String value = config.getString(path).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path + " must not be empty");
        }
        return value;
    }
}

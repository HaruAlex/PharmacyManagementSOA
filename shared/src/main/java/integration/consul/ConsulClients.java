package integration.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.typesafe.config.Config;

import java.net.URI;

public final class ConsulClients {
    private ConsulClients() {
    }

    public static ConsulClient create(Config config) {
        if (config.hasPath("consul.address")) {
            URI address = URI.create(config.getString("consul.address"));
            if (!"http".equalsIgnoreCase(address.getScheme())
                    || address.getHost() == null
                    || address.getPort() < 1
                    || address.getRawPath() != null && !address.getRawPath().isEmpty()) {
                throw new IllegalArgumentException("consul.address must be an HTTP URL with a host and port");
            }
            return new ConsulClient(address.getHost(), address.getPort());
        }

        String host = config.getString("consul.host");
        int port = config.getInt("consul.port");
        if (host.isBlank() || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Consul host and port must be valid");
        }
        return new ConsulClient(host, port);
    }
}

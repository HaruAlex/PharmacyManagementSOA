package integration.consul;

import com.ecwid.consul.v1.health.model.HealthService;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ServiceResolver {
    List<HealthService> getHealthyInstances(String serviceName);

    Optional<HealthService> resolve(String serviceName);

    Optional<HealthService> resolve(String serviceName, Set<String> excludedInstanceIds);
}

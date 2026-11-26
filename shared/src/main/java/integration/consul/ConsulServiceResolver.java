package integration.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.health.model.HealthService;
import com.ecwid.consul.v1.QueryParams;
import play.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConsulServiceResolver implements ServiceResolver {
    private final ConsulClient consulClient;
    private final long cacheTtlMillis;
    private final ConcurrentHashMap<String, CachedInstances> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> refreshLocks = new ConcurrentHashMap<>();

    public ConsulServiceResolver(ConsulClient consulClient, long cacheTtlMillis) {
        if (cacheTtlMillis < 0) {
            throw new IllegalArgumentException("Consul discovery cache TTL cannot be negative");
        }
        this.consulClient = consulClient;
        this.cacheTtlMillis = cacheTtlMillis;
    }

    @Override
    public List<HealthService> getHealthyInstances(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be empty");
        }

        CachedInstances cached = cache.get(serviceName);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAtMillis < cacheTtlMillis) {
            return cached.instances;
        }

        Object lock = refreshLocks.computeIfAbsent(serviceName, ignored -> new Object());
        synchronized (lock) {
            cached = cache.get(serviceName);
            now = System.currentTimeMillis();
            if (cached != null && now - cached.fetchedAtMillis < cacheTtlMillis) {
                return cached.instances;
            }

            try {
                Response<List<HealthService>> response =
                        consulClient.getHealthServices(serviceName, true, QueryParams.DEFAULT);
                List<HealthService> services = response == null || response.getValue() == null
                        ? Collections.emptyList()
                        : new ArrayList<>(response.getValue());
                services.sort((left, right) -> serviceId(left).compareTo(serviceId(right)));
                CachedInstances refreshed = new CachedInstances(
                        Collections.unmodifiableList(services),
                        System.currentTimeMillis());
                cache.put(serviceName, refreshed);
                Logger.debug("[ServiceDiscovery] " + serviceName + ": "
                        + services.size() + " passing instances");
                return refreshed.instances;
            } catch (RuntimeException e) {
                throw new ServiceDiscoveryException("Consul lookup failed for " + serviceName, e);
            }
        }
    }

    @Override
    public Optional<HealthService> resolve(String serviceName) {
        return resolve(serviceName, Collections.emptySet());
    }

    @Override
    public Optional<HealthService> resolve(String serviceName, Set<String> excludedInstanceIds) {
        List<HealthService> candidates = getHealthyInstances(serviceName);
        if (excludedInstanceIds != null && !excludedInstanceIds.isEmpty()) {
            candidates = candidates.stream()
                    .filter(instance -> !excludedInstanceIds.contains(serviceId(instance)))
                    .toList();
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int index = Math.floorMod(
                counters.computeIfAbsent(serviceName, ignored -> new AtomicInteger()).getAndIncrement(),
                candidates.size());
        return Optional.of(candidates.get(index));
    }

    public static String serviceId(HealthService instance) {
        if (instance == null || instance.getService() == null
                || instance.getService().getId() == null) {
            throw new IllegalArgumentException("Consul returned a service without an ID");
        }
        return instance.getService().getId();
    }

    public static String baseUrl(HealthService instance) {
        if (instance == null || instance.getService() == null) {
            throw new IllegalArgumentException("Consul returned an incomplete service instance");
        }
        String address = instance.getService().getAddress();
        if ((address == null || address.isBlank()) && instance.getNode() != null) {
            address = instance.getNode().getAddress();
        }
        int port = instance.getService().getPort();
        if (address == null || address.isBlank() || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Consul returned an invalid service address or port");
        }
        return "http://" + address + ":" + port;
    }

    private static final class CachedInstances {
        private final List<HealthService> instances;
        private final long fetchedAtMillis;

        private CachedInstances(List<HealthService> instances, long fetchedAtMillis) {
            this.instances = instances;
            this.fetchedAtMillis = fetchedAtMillis;
        }
    }
}

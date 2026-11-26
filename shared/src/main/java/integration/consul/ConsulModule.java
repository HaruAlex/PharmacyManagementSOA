package integration.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.typesafe.config.Config;

import javax.inject.Singleton;

public final class ConsulModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ConsulServiceRegistration.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    public ConsulClient consulClient(Config config) {
        return ConsulClients.create(config);
    }

    @Provides
    @Singleton
    public ServiceResolver serviceResolver(ConsulClient consulClient, Config config) {
        long cacheTtlMillis = config.getLong("consul.discovery.cache-ttl-ms");
        return new ConsulServiceResolver(consulClient, cacheTtlMillis);
    }
}

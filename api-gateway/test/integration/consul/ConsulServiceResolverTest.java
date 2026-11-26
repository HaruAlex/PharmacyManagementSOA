package integration.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.health.model.HealthService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsulServiceResolverTest {
    @Test
    void selectsOnlyPassingInstancesInRoundRobinOrder() {
        ConsulClient consulClient = mock(ConsulClient.class);
        Response<List<HealthService>> response = mock(Response.class);
        when(consulClient.getHealthServices(eq("catalog-service"), eq(true), any(QueryParams.class)))
                .thenReturn(response);
        when(response.getValue()).thenReturn(List.of(instance("catalog-1"), instance("catalog-2")));
        ConsulServiceResolver resolver = new ConsulServiceResolver(consulClient, 60_000);

        assertThat(ConsulServiceResolver.serviceId(resolver.resolve("catalog-service").orElseThrow()))
                .isEqualTo("catalog-1");
        assertThat(ConsulServiceResolver.serviceId(resolver.resolve("catalog-service").orElseThrow()))
                .isEqualTo("catalog-2");
        verify(consulClient).getHealthServices(eq("catalog-service"), eq(true), any(QueryParams.class));
    }

    @Test
    void resolveExcludesFailedInstanceIds() {
        ConsulClient consulClient = mock(ConsulClient.class);
        Response<List<HealthService>> response = mock(Response.class);
        when(consulClient.getHealthServices(eq("catalog-service"), eq(true), any(QueryParams.class)))
                .thenReturn(response);
        when(response.getValue()).thenReturn(List.of(instance("catalog-1"), instance("catalog-2")));
        ConsulServiceResolver resolver = new ConsulServiceResolver(consulClient, 60_000);

        Optional<HealthService> selected = resolver.resolve("catalog-service", Set.of("catalog-1"));

        assertThat(selected).isPresent();
        assertThat(ConsulServiceResolver.serviceId(selected.orElseThrow())).isEqualTo("catalog-2");
    }

    private HealthService instance(String id) {
        HealthService.Service service = new HealthService.Service();
        service.setId(id);
        service.setAddress("catalog");
        service.setPort(9002);
        HealthService instance = new HealthService();
        instance.setService(service);
        return instance;
    }
}

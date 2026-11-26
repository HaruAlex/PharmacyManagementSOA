package services;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRouteConfigTest {
    @Test
    void routeForPathUsesLongestConfiguredPrefix() {
        GatewayRouteConfig routes = new GatewayRouteConfig(config());

        GatewayRouteConfig.Route route = routes.findRoute("/api/inventory/ledger/17").orElseThrow();

        assertThat(route.getServiceName()).isEqualTo("inventory-service");
        assertThat(route.getUpstreamPrefix()).isEqualTo("/api/v1/inventory/ledger");
    }

    @Test
    void publicAndRoleRulesMatchBothPathAndMethod() {
        GatewayRouteConfig routes = new GatewayRouteConfig(config());

        assertThat(routes.isPublic("/api/auth/login", "POST")).isTrue();
        assertThat(routes.isPublic("/api/auth/login", "GET")).isFalse();
        assertThat(routes.isPublic("/api/auth/login/extra", "POST")).isFalse();
        assertThat(routes.allowedRoles("/api/sales/invoices", "POST"))
                .contains(Set.of("AD", "SP"));
        assertThat(routes.allowedRoles("/api/sales/invoices", "OPTIONS")).isEmpty();
    }

    @Test
    void stubServiceProfileAddsRouteAndRoleWithoutGatewayCodeChanges() {
        Config stubConfig = ConfigFactory.load("application.stub.conf");
        GatewayRouteConfig routes = new GatewayRouteConfig(stubConfig);

        assertThat(routes.findRoute("/api/stub/items/7"))
                .hasValueSatisfying(route -> assertThat(route.getServiceName()).isEqualTo("gateway-stub"));
        assertThat(routes.allowedRoles("/api/stub/items/7", "GET"))
                .contains(Set.of("AD", "WH", "SP"));
    }

    private Config config() {
        return ConfigFactory.parseString(
                "gateway.routes = ["
                        + "{ public-prefix=\"/api/inventory\", service-name=\"inventory-service\", upstream-prefix=\"/api/inventory\" },"
                        + "{ public-prefix=\"/api/inventory/ledger\", service-name=\"inventory-service\", upstream-prefix=\"/api/v1/inventory/ledger\" }"
                        + "]\n"
                        + "gateway.public-routes = [{path-prefix=\"/api/auth/login\", methods=[\"POST\"], exact=true}]\n"
                        + "gateway.role-rules = ["
                        + "{path-prefix=\"/api/inventory\", methods=[\"GET\"], roles=[\"AD\", \"WH\", \"SP\"]},"
                        + "{path-prefix=\"/api/sales\", methods=[\"POST\"], roles=[\"AD\", \"SP\"]}]");
    }
}

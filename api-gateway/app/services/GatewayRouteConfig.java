package services;

import com.typesafe.config.Config;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public final class GatewayRouteConfig {
    private final List<Route> routes;
    private final List<PublicRoute> publicRoutes;
    private final List<RoleRule> roleRules;

    @Inject
    public GatewayRouteConfig(Config config) {
        this.routes = config.getConfigList("gateway.routes").stream()
                .map(route -> new Route(
                        requiredPrefix(route.getString("public-prefix"), "gateway.routes.public-prefix"),
                        required(route.getString("service-name"), "gateway.routes.service-name"),
                        normalizeUpstreamPrefix(route.getString("upstream-prefix"))))
                .sorted(Comparator.comparingInt((Route route) -> route.publicPrefix.length()).reversed())
                .toList();
        this.publicRoutes = config.getConfigList("gateway.public-routes").stream()
                .map(route -> new PublicRoute(
                        requiredPrefix(route.getString("path-prefix"), "gateway.public-routes.path-prefix"),
                        methods(route.getStringList("methods")),
                        route.hasPath("exact") && route.getBoolean("exact")))
                .sorted(Comparator.comparingInt((PublicRoute route) -> route.pathPrefix.length()).reversed())
                .toList();
        this.roleRules = config.getConfigList("gateway.role-rules").stream()
                .map(rule -> new RoleRule(
                        requiredPrefix(rule.getString("path-prefix"), "gateway.role-rules.path-prefix"),
                        methods(rule.getStringList("methods")),
                        Set.copyOf(rule.getStringList("roles"))))
                .sorted(Comparator.comparingInt((RoleRule rule) -> rule.pathPrefix.length()).reversed())
                .toList();
        if (routes.isEmpty()) {
            throw new IllegalArgumentException("gateway.routes must contain at least one route");
        }
        for (Route route : routes) {
            boolean hasRolePolicy = roleRules.stream()
                    .anyMatch(rule -> matchesPrefix(route.publicPrefix, rule.pathPrefix));
            boolean isPublicRoute = publicRoutes.stream()
                    .anyMatch(publicRoute -> !publicRoute.exact
                            && matchesPrefix(route.publicPrefix, publicRoute.pathPrefix));
            if (!hasRolePolicy && !isPublicRoute) {
                throw new IllegalArgumentException(
                        "Gateway route has no role or public policy: " + route.publicPrefix);
            }
        }
    }

    public Optional<Route> findRoute(String path) {
        return routes.stream()
                .filter(route -> matchesPrefix(path, route.publicPrefix))
                .findFirst();
    }

    public boolean isPublic(String path, String method) {
        String normalizedMethod = normalizedMethod(method);
        return publicRoutes.stream().anyMatch(route ->
                (route.exact ? path.equals(route.pathPrefix) : matchesPrefix(path, route.pathPrefix))
                        && route.methods.contains(normalizedMethod));
    }

    public Optional<Set<String>> allowedRoles(String path, String method) {
        String normalizedMethod = normalizedMethod(method);
        return roleRules.stream()
                .filter(rule -> matchesPrefix(path, rule.pathPrefix)
                        && rule.methods.contains(normalizedMethod))
                .map(rule -> rule.roles)
                .findFirst();
    }

    public static boolean matchesPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static String normalizedMethod(String method) {
        String normalized = method.toUpperCase(Locale.ROOT);
        return "HEAD".equals(normalized) ? "GET" : normalized;
    }

    private static String requiredPrefix(String value, String path) {
        String prefix = required(value, path);
        if (!prefix.startsWith("/") || prefix.length() > 1 && prefix.endsWith("/")) {
            throw new IllegalArgumentException(path + " must start with / and have no trailing slash");
        }
        return prefix;
    }

    private static String normalizeUpstreamPrefix(String value) {
        if (value.isBlank()) {
            return "";
        }
        String prefix = value.startsWith("/") ? value : "/" + value;
        if (prefix.endsWith("/") && prefix.length() > 1) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    private static Set<String> methods(List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Gateway route methods must not be empty");
        }
        return values.stream()
                .map(method -> method.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String required(String value, String path) {
        String result = value.trim();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(path + " must not be empty");
        }
        return result;
    }

    public static final class Route {
        private final String publicPrefix;
        private final String serviceName;
        private final String upstreamPrefix;

        private Route(String publicPrefix, String serviceName, String upstreamPrefix) {
            this.publicPrefix = publicPrefix;
            this.serviceName = serviceName;
            this.upstreamPrefix = upstreamPrefix;
        }

        public String getPublicPrefix() {
            return publicPrefix;
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getUpstreamPrefix() {
            return upstreamPrefix;
        }
    }

    private static final class PublicRoute {
        private final String pathPrefix;
        private final Set<String> methods;
        private final boolean exact;

        private PublicRoute(String pathPrefix, Set<String> methods, boolean exact) {
            this.pathPrefix = pathPrefix;
            this.methods = methods;
            this.exact = exact;
        }
    }

    private static final class RoleRule {
        private final String pathPrefix;
        private final Set<String> methods;
        private final Set<String> roles;

        private RoleRule(String pathPrefix, Set<String> methods, Set<String> roles) {
            if (roles.isEmpty()) {
                throw new IllegalArgumentException("Gateway route roles must not be empty");
            }
            this.pathPrefix = pathPrefix;
            this.methods = methods;
            this.roles = roles;
        }
    }
}

package controllers;

import com.auth0.jwt.JWT;
import com.fasterxml.jackson.databind.JsonNode;
import domain.AuditLog;
import domain.Permission;
import domain.RefreshToken;
import domain.Role;
import domain.RoleCodes;
import domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import integration.consul.ServiceResolver;
import play.Application;
import play.db.ConnectionCallable;
import play.db.Database;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import repositories.AuditLogRepository;
import repositories.PermissionRepository;
import repositories.RefreshTokenRepository;
import repositories.RolePermissionRepository;
import repositories.RoleRepository;
import repositories.UserBranchRepository;
import repositories.UserRepository;
import security.BCryptPasswordHasher;

import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static play.inject.Bindings.bind;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.route;
import static play.test.Helpers.start;
import static play.test.Helpers.stop;

class AuthPhase7EndToEndTest {
    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final Instant START = Instant.parse("2026-11-26T00:00:00Z");

    private Application application;
    private MutableClock clock;

    @BeforeEach
    void startApplication() {
        clock = new MutableClock(START);
        InMemoryAuthStore store = new InMemoryAuthStore(clock);
        store.addRole(new Role(1, RoleCodes.ADMIN, "Administrator", true));
        store.addRole(new Role(2, RoleCodes.WAREHOUSE, "Warehouse", true));
        store.addRole(new Role(3, RoleCodes.SALES_PHARMACIST, "Pharmacist", true));
        store.addPermission(new Permission(1, "USER_MANAGE", "Manage users"));
        store.replaceRolePermissionIds(1, List.of(1));

        application = new GuiceApplicationBuilder()
                .configure("play.modules.disabled", List.of(
                        "modules.StartupModule",
                        "integration.consul.ConsulModule"))
                .configure("security.rateLimit.login.maxPerIdentity", 100)
                .configure("security.rateLimit.login.maxPerIp", 1000)
                .configure("security.rateLimit.refresh.maxPerIdentity", 100)
                .configure("security.rateLimit.refresh.maxPerIp", 1000)
                .configure("security.rateLimit.changePassword.maxPerIdentity", 100)
                .configure("security.rateLimit.changePassword.maxPerIp", 1000)
                .overrides(
                        bind(Clock.class).toInstance(clock),
                        bind(UserRepository.class).toInstance(store),
                        bind(RoleRepository.class).toInstance(new InMemoryRoleRepository(store)),
                        bind(PermissionRepository.class).toInstance(new InMemoryPermissionRepository(store)),
                        bind(RolePermissionRepository.class).toInstance(new InMemoryRolePermissionRepository(store)),
                        bind(UserBranchRepository.class).toInstance(store),
                        bind(RefreshTokenRepository.class).toInstance(store),
                        bind(AuditLogRepository.class).toInstance(store),
                        bind(Database.class).toInstance(inMemoryDatabase()),
                        bind(ServiceResolver.class).toInstance(mock(ServiceResolver.class)))
                .build();
        start(application);
    }

    @AfterEach
    void stopApplication() {
        if (application != null) {
            stop(application);
        }
    }

    @Test
    void login_refresh_branchSelection_logout_rejectsReplayedRefreshToken() {
        JsonNode login = login("admin.demo", ADMIN_PASSWORD, 200);
        String refreshToken = login.path("refreshToken").asText();
        assertThat(login.path("accessToken").asText()).isNotBlank();
        assertThat(refreshToken).isNotBlank();
        expectStatus(call("GET", "/api/auth/me", null, login.path("accessToken").asText()), 200);

        JsonNode refreshed = expectJson(call(
                "POST", "/api/auth/refresh", object("refreshToken", refreshToken), null), 200);
        String rotatedRefreshToken = refreshed.path("refreshToken").asText();
        assertThat(rotatedRefreshToken).isNotBlank().isNotEqualTo(refreshToken);

        JsonNode branchSelection = expectJson(call(
                "PUT",
                "/api/auth/current-branch",
                object("branchId", 1),
                refreshed.path("accessToken").asText()), 200);
        String selectedBranchAccessToken = branchSelection.path("accessToken").asText();
        assertThat(JWT.decode(selectedBranchAccessToken)
                .getClaim("current_branch_id").asInt()).isEqualTo(1);

        expectStatus(call(
                "POST",
                "/api/auth/logout",
                object("refreshToken", rotatedRefreshToken),
                selectedBranchAccessToken), 200);
        expectStatus(call(
                "POST",
                "/api/auth/refresh",
                object("refreshToken", rotatedRefreshToken),
                null), 401);
    }

    @Test
    void login_fiveWrongPasswords_locksAccount() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            expectApiExceptionStatus("POST", "/api/auth/login", object(
                    "usernameOrEmail", "pharmacist.demo",
                    "password", "WrongPassword@" + attempt), null, attempt == 5 ? 423 : 401);
        }

        expectApiExceptionStatus("POST", "/api/auth/login", object(
                "usernameOrEmail", "pharmacist.demo",
                "password", "Pharmacist@12345"), null, 423);
    }

    @Test
    void adminCreateUser_thenUserCanLogin() {
        String adminAccessToken = login("admin.demo", ADMIN_PASSWORD, 200)
                .path("accessToken").asText();
        JsonNode createdUser = createUser(adminAccessToken);

        JsonNode userLogin = login("phase7.user", "Strong@Pass123", 200);
        expectStatus(call("GET", "/api/auth/me", null, userLogin.path("accessToken").asText()), 200);
        assertThat(createdUser.path("userId").asInt()).isPositive();
    }

    @Test
    void adminLockUser_invalidatesAccessAndRefreshTokens() {
        String adminAccessToken = login("admin.demo", ADMIN_PASSWORD, 200)
                .path("accessToken").asText();
        int userId = createUser(adminAccessToken).path("userId").asInt();
        JsonNode userLogin = login("phase7.user", "Strong@Pass123", 200);

        expectStatus(call("POST", "/api/users/" + userId + "/lock", null, adminAccessToken), 200);
        expectApiExceptionStatus(
                "GET", "/api/auth/me", null, userLogin.path("accessToken").asText(), 401);
        expectStatus(call(
                "POST",
                "/api/auth/refresh",
                object("refreshToken", userLogin.path("refreshToken").asText()),
                null), 401);
    }

    @Test
    void refresh_afterSevenDays_returnsExpired() {
        JsonNode login = login("warehouse.demo", "Warehouse@12345", 200);
        clock.advance(Duration.ofDays(7).plusSeconds(1));

        JsonNode expiredRefresh = expectJson(call(
                "POST",
                "/api/auth/refresh",
                object("refreshToken", login.path("refreshToken").asText()),
                null), 401);
        assertThat(expiredRefresh.path("code").asText()).isEqualTo("AUTH_REFRESH_EXPIRED");
    }

    private JsonNode login(String usernameOrEmail, String password, int expectedStatus) {
        return expectJson(call("POST", "/api/auth/login", object(
                "usernameOrEmail", usernameOrEmail,
                "password", password), null), expectedStatus);
    }

    private JsonNode createUser(String adminAccessToken) {
        return expectJson(call(
                "POST",
                "/api/users",
                object(
                        "fullName", "Phase 7 User",
                        "username", "phase7.user",
                        "password", "Strong@Pass123",
                        "email", "phase7.user@example.local",
                        "phone", "0900000000",
                        "roleId", 3,
                        "branchIds", List.of(1)),
                adminAccessToken), 201);
    }

    private Result call(String method, String path, JsonNode body, String accessToken) {
        Http.RequestBuilder request = fakeRequest(method, path);
        if (body != null) {
            request = request.bodyJson(body);
        }
        if (accessToken != null) {
            request = request.header("Authorization", "Bearer " + accessToken);
        }
        return route(application, request);
    }

    private JsonNode expectJson(Result result, int expectedStatus) {
        assertThat(result.status()).isEqualTo(expectedStatus);
        return Json.parse(contentAsString(result));
    }

    private void expectStatus(Result result, int expectedStatus) {
        assertThat(result.status()).isEqualTo(expectedStatus);
    }

    private void expectApiExceptionStatus(
            String method,
            String path,
            JsonNode body,
            String accessToken,
            int expectedStatus) {
        try {
            call(method, path, body, accessToken);
            throw new AssertionError("Expected API error with status " + expectedStatus);
        } catch (errors.ApiException error) {
            assertThat(error.getStatus()).isEqualTo(expectedStatus);
        }
    }

    private JsonNode object(Object... fields) {
        var node = Json.newObject();
        for (int index = 0; index < fields.length; index += 2) {
            String name = (String) fields[index];
            Object value = fields[index + 1];
            if (value instanceof String) {
                node.put(name, (String) value);
            } else if (value instanceof Integer) {
                node.put(name, (Integer) value);
            } else if (value instanceof List<?>) {
                node.set(name, Json.toJson(value));
            } else {
                throw new IllegalArgumentException("Unsupported JSON field value: " + name);
            }
        }
        return node;
    }

    private static Database inMemoryDatabase() {
        return mock(Database.class, invocation -> {
            if ("withTransaction".equals(invocation.getMethod().getName())) {
                ConnectionCallable<?> transaction = invocation.getArgument(0);
                return transaction.call(mock(Connection.class));
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class InMemoryRoleRepository implements RoleRepository {
        private final InMemoryAuthStore store;

        private InMemoryRoleRepository(InMemoryAuthStore store) {
            this.store = store;
        }

        @Override
        public Optional<Role> getById(Integer roleId) {
            return store.getRoleById(roleId);
        }

        @Override
        public Optional<Role> getByCode(String roleCode) {
            return store.getRoleByCode(roleCode);
        }

        @Override
        public List<Role> getAll() {
            return store.getAllRoles();
        }

        @Override
        public List<Role> getAllActive() {
            return store.getActiveRoles();
        }

        @Override
        public boolean existsByCode(String roleCode) {
            return store.roleCodeExists(roleCode);
        }

        @Override
        public Integer create(Role role) {
            return store.createRole(role);
        }

        @Override
        public void update(Role role) {
            store.updateRole(role);
        }

        @Override
        public void setActive(Integer roleId, boolean active) {
            store.setRoleActive(roleId, active);
        }

        @Override
        public boolean isInUseByActiveUsers(Integer roleId) {
            return store.roleIsInUseByActiveUsers(roleId);
        }

        @Override
        public long countActiveUsersByRole(Integer roleId) {
            return store.countActiveUsersByRole(roleId);
        }

        @Override
        public List<Integer> getUserIdsByRoleId(Integer roleId) {
            return store.getUserIdsByRoleId(roleId);
        }
    }

    private static final class InMemoryPermissionRepository implements PermissionRepository {
        private final InMemoryAuthStore store;

        private InMemoryPermissionRepository(InMemoryAuthStore store) {
            this.store = store;
        }

        @Override
        public Optional<Permission> getById(Integer permissionId) {
            return store.getPermissionById(permissionId);
        }

        @Override
        public Optional<Permission> getByCode(String permissionCode) {
            return store.getPermissionByCode(permissionCode);
        }

        @Override
        public List<Permission> getAll() {
            return store.getAllPermissions();
        }

        @Override
        public List<Permission> getByRoleId(Integer roleId) {
            return store.getPermissionsByRoleId(roleId);
        }

        @Override
        public List<Permission> getByUserId(Integer userId) {
            return store.getPermissionsByUserId(userId);
        }

        @Override
        public boolean existsByCode(String permissionCode) {
            return store.permissionCodeExists(permissionCode);
        }

        @Override
        public Integer create(Permission permission) {
            return store.createPermission(permission);
        }

        @Override
        public void update(Permission permission) {
            store.updatePermission(permission);
        }

        @Override
        public boolean isAssignedToAnyRole(Integer permissionId) {
            return store.permissionIsAssignedToAnyRole(permissionId);
        }

        @Override
        public void replaceRolePermissions(Integer roleId, List<Integer> permissionIds) {
            store.replaceRolePermissionIds(roleId, permissionIds);
        }
    }

    private static final class InMemoryRolePermissionRepository implements RolePermissionRepository {
        private final InMemoryAuthStore store;

        private InMemoryRolePermissionRepository(InMemoryAuthStore store) {
            this.store = store;
        }

        @Override
        public void replacePermissions(Integer roleId, List<Integer> permissionIds) {
            store.replaceRolePermissionIds(roleId, permissionIds);
        }

        @Override
        public void deleteByRoleId(Integer roleId) {
            store.deleteRolePermissions(roleId);
        }
    }

    private static final class InMemoryAuthStore implements
            UserRepository,
            UserBranchRepository,
            RefreshTokenRepository,
            AuditLogRepository {
        private final Clock clock;
        private final Map<Integer, User> users = new LinkedHashMap<>();
        private final Map<Integer, Role> roles = new LinkedHashMap<>();
        private final Map<Integer, Permission> permissions = new LinkedHashMap<>();
        private final Map<Integer, List<Integer>> branchesByUser = new HashMap<>();
        private final Map<Integer, List<Integer>> permissionsByRole = new HashMap<>();
        private final Map<Long, RefreshToken> refreshTokens = new LinkedHashMap<>();
        private final List<AuditLog> auditLogs = new ArrayList<>();
        private int nextUserId = 1;
        private int nextRoleId = 1;
        private int nextPermissionId = 1;
        private long nextRefreshTokenId = 1;
        private long nextAuditLogId = 1;

        private InMemoryAuthStore(Clock clock) {
            this.clock = clock;
        }

        private void addRole(Role role) {
            roles.put(role.getRoleId(), role);
            nextRoleId = Math.max(nextRoleId, role.getRoleId() + 1);
        }

        private void addPermission(Permission permission) {
            permissions.put(permission.getPermissionId(), permission);
            nextPermissionId = Math.max(nextPermissionId, permission.getPermissionId() + 1);
        }

        @Override
        public Optional<User> getById(Integer userId) {
            return Optional.ofNullable(users.get(userId)).map(this::copyUser);
        }

        @Override
        public Optional<User> getByUsername(String username) {
            String normalized = normalize(username);
            return users.values().stream()
                    .filter(user -> normalize(user.getUsername()).equals(normalized))
                    .findFirst()
                    .map(this::copyUser);
        }

        @Override
        public Optional<User> getByEmail(String email) {
            String normalized = normalize(email);
            return users.values().stream()
                    .filter(user -> normalize(user.getEmail()).equals(normalized))
                    .findFirst()
                    .map(this::copyUser);
        }

        @Override
        public Optional<User> getByUsernameOrEmail(String usernameOrEmail) {
            String normalized = normalize(usernameOrEmail);
            return users.values().stream()
                    .filter(user -> normalize(user.getUsername()).equals(normalized)
                            || normalize(user.getEmail()).equals(normalized))
                    .findFirst()
                    .map(this::copyUser);
        }

        @Override
        public boolean existsUsername(String username) {
            return getByUsername(username).isPresent();
        }

        @Override
        public boolean existsEmail(String email) {
            return getByEmail(email).isPresent();
        }

        @Override
        public List<User> search(
                String nameFilter,
                String emailFilter,
                Integer roleId,
                Boolean isActive,
                Boolean isLocked,
                int page,
                int pageSize,
                String sortBy,
                String sortOrder) {
            Comparator<User> comparator = Comparator.comparing(
                    user -> "email".equalsIgnoreCase(sortBy)
                            ? normalize(user.getEmail())
                            : "fullName".equalsIgnoreCase(sortBy)
                                    ? normalize(user.getFullName())
                                    : normalize(user.getUsername()));
            if ("DESC".equalsIgnoreCase(sortOrder)) {
                comparator = comparator.reversed();
            }
            return filtered(nameFilter, emailFilter, roleId, isActive, isLocked).stream()
                    .sorted(comparator)
                    .skip((long) Math.max(0, page - 1) * pageSize)
                    .limit(pageSize)
                    .collect(Collectors.toList());
        }

        @Override
        public long count(
                String nameFilter,
                String emailFilter,
                Integer roleId,
                Boolean isActive,
                Boolean isLocked) {
            return filtered(nameFilter, emailFilter, roleId, isActive, isLocked).size();
        }

        private List<User> filtered(
                String nameFilter,
                String emailFilter,
                Integer roleId,
                Boolean isActive,
                Boolean isLocked) {
            return users.values().stream()
                    .filter(user -> nameFilter == null
                            || normalize(user.getFullName()).contains(normalize(nameFilter))
                            || normalize(user.getUsername()).contains(normalize(nameFilter)))
                    .filter(user -> emailFilter == null
                            || normalize(user.getEmail()).contains(normalize(emailFilter)))
                    .filter(user -> roleId == null || roleId.equals(user.getRoleId()))
                    .filter(user -> isActive == null || isActive == user.isActive())
                    .filter(user -> isLocked == null || isLocked == user.isLocked())
                    .collect(Collectors.toList());
        }

        private User copyUser(User user) {
            User copy = new User();
            copy.setUserId(user.getUserId());
            copy.setRoleId(user.getRoleId());
            copy.setUsername(user.getUsername());
            copy.setPasswordHash(user.getPasswordHash());
            copy.setFullName(user.getFullName());
            copy.setPhone(user.getPhone());
            copy.setEmail(user.getEmail());
            copy.setLocked(user.isLocked());
            copy.setFailedLoginCount(user.getFailedLoginCount());
            copy.setActive(user.isActive());
            copy.setCreatedAt(user.getCreatedAt());
            copy.setUpdatedAt(user.getUpdatedAt());
            return copy;
        }

        @Override
        public Integer create(User user) {
            int userId = nextUserId++;
            user.setUserId(userId);
            users.put(userId, user);
            return userId;
        }

        @Override
        public void update(User user) {
            users.put(user.getUserId(), user);
        }

        @Override
        public void setLocked(Integer userId, boolean locked) {
            User user = users.get(userId);
            if (user != null) {
                user.setLocked(locked);
            }
        }

        @Override
        public void updatePasswordHash(Integer userId, String passwordHash) {
            User user = users.get(userId);
            if (user != null) {
                user.setPasswordHash(passwordHash);
            }
        }

        @Override
        public void incrementFailedLogin(Integer userId) {
            User user = users.get(userId);
            if (user != null) {
                user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            }
        }

        @Override
        public void resetFailedLogin(Integer userId) {
            User user = users.get(userId);
            if (user != null) {
                user.setFailedLoginCount(0);
            }
        }

        @Override
        public void setActive(Integer userId, boolean active) {
            User user = users.get(userId);
            if (user != null) {
                user.setActive(active);
            }
        }

        @Override
        public long countActiveAdmins() {
            return users.values().stream()
                    .filter(user -> user.isActive() && !user.isLocked())
                    .filter(user -> {
                        Role role = roles.get(user.getRoleId());
                        return role != null && RoleCodes.ADMIN.equals(role.getRoleCode());
                    })
                    .count();
        }

        @Override
        public List<Integer> getUserIdsByRoleId(Integer roleId) {
            return users.values().stream()
                    .filter(user -> roleId.equals(user.getRoleId()))
                    .map(User::getUserId)
                    .collect(Collectors.toList());
        }

        private Optional<Role> getRoleById(Integer roleId) {
            return Optional.ofNullable(roles.get(roleId));
        }

        private Optional<Role> getRoleByCode(String roleCode) {
            return roles.values().stream()
                    .filter(role -> role.getRoleCode().equalsIgnoreCase(roleCode))
                    .findFirst();
        }

        private List<Role> getAllRoles() {
            return new ArrayList<>(roles.values());
        }

        private List<Role> getActiveRoles() {
            return roles.values().stream().filter(Role::isActive).collect(Collectors.toList());
        }

        private boolean roleCodeExists(String roleCode) {
            return getRoleByCode(roleCode).isPresent();
        }

        private Integer createRole(Role role) {
            int roleId = nextRoleId++;
            role.setRoleId(roleId);
            roles.put(roleId, role);
            return roleId;
        }

        private void updateRole(Role role) {
            roles.put(role.getRoleId(), role);
        }

        private void setRoleActive(Integer roleId, boolean active) {
            Role role = roles.get(roleId);
            if (role != null) {
                role.setActive(active);
            }
        }

        private boolean roleIsInUseByActiveUsers(Integer roleId) {
            return countActiveUsersByRole(roleId) > 0;
        }

        private long countActiveUsersByRole(Integer roleId) {
            return users.values().stream()
                    .filter(user -> user.isActive() && roleId.equals(user.getRoleId()))
                    .count();
        }

        private Optional<Permission> getPermissionById(Integer permissionId) {
            return Optional.ofNullable(permissions.get(permissionId));
        }

        private Optional<Permission> getPermissionByCode(String permissionCode) {
            return permissions.values().stream()
                    .filter(permission -> permission.getPermissionCode().equalsIgnoreCase(permissionCode))
                    .findFirst();
        }

        private List<Permission> getAllPermissions() {
            return new ArrayList<>(permissions.values());
        }

        private List<Permission> getPermissionsByRoleId(Integer roleId) {
            return permissionsByRole.getOrDefault(roleId, List.of()).stream()
                    .map(permissions::get)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        }

        private List<Permission> getPermissionsByUserId(Integer userId) {
            User user = users.get(userId);
            return user == null ? List.of() : getPermissionsByRoleId(user.getRoleId());
        }

        private boolean permissionCodeExists(String permissionCode) {
            return getPermissionByCode(permissionCode).isPresent();
        }

        private Integer createPermission(Permission permission) {
            int permissionId = nextPermissionId++;
            permission.setPermissionId(permissionId);
            permissions.put(permissionId, permission);
            return permissionId;
        }

        private void updatePermission(Permission permission) {
            permissions.put(permission.getPermissionId(), permission);
        }

        private boolean permissionIsAssignedToAnyRole(Integer permissionId) {
            return permissionsByRole.values().stream().anyMatch(ids -> ids.contains(permissionId));
        }

        private void replaceRolePermissionIds(Integer roleId, List<Integer> permissionIds) {
            permissionsByRole.put(roleId, List.copyOf(permissionIds));
        }

        private void deleteRolePermissions(Integer roleId) {
            permissionsByRole.remove(roleId);
        }

        @Override
        public List<Integer> getBranchIdsByUserId(Integer userId) {
            return branchesByUser.getOrDefault(userId, List.of());
        }

        @Override
        public void replaceBranches(Integer userId, List<Integer> branchIds) {
            branchesByUser.put(userId, List.copyOf(branchIds));
        }

        @Override
        public void deleteByUserId(Integer userId) {
            branchesByUser.remove(userId);
        }

        @Override
        public Optional<RefreshToken> getByTokenHash(String tokenHash) {
            return refreshTokens.values().stream()
                    .filter(token -> token.getTokenHash().equals(tokenHash))
                    .findFirst();
        }

        @Override
        public Long create(RefreshToken token) {
            long tokenId = nextRefreshTokenId++;
            token.setRefreshTokenId(tokenId);
            refreshTokens.put(tokenId, token);
            return tokenId;
        }

        @Override
        public void revoke(Long refreshTokenId) {
            RefreshToken token = refreshTokens.get(refreshTokenId);
            if (token != null && !token.isRevoked()) {
                token.setRevokedAt(clock.instant());
            }
        }

        @Override
        public int revokeAllByUserId(Integer userId) {
            int revoked = 0;
            for (RefreshToken token : refreshTokens.values()) {
                if (userId.equals(token.getUserId()) && !token.isRevoked()) {
                    token.setRevokedAt(clock.instant());
                    revoked++;
                }
            }
            return revoked;
        }

        @Override
        public int deleteExpired() {
            int before = refreshTokens.size();
            refreshTokens.values().removeIf(token -> token.isExpired(clock.instant()));
            return before - refreshTokens.size();
        }

        @Override
        public Long write(AuditLog auditLog) {
            auditLogs.add(auditLog);
            return nextAuditLogId++;
        }

        @Override
        public List<AuditLog> search(
                Integer userId,
                String actionCode,
                String entityName,
                Instant fromDate,
                Instant toDate,
                int page,
                int pageSize,
                String sortOrder) {
            return new ArrayList<>(auditLogs);
        }

        @Override
        public long count(
                Integer userId,
                String actionCode,
                String entityName,
                Instant fromDate,
                Instant toDate) {
            return auditLogs.size();
        }

        private String normalize(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }
    }
}

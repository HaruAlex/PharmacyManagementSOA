package services;

import clients.BranchLookupClient;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import domain.Role;
import domain.User;
import dto.CreateUserRequest;
import dto.UpdateRoleBranchesRequest;
import dto.UpdateUserRequest;
import dto.UserResponse;
import errors.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import play.db.ConnectionCallable;
import play.db.Database;
import repositories.RefreshTokenRepository;
import repositories.RoleRepository;
import repositories.UserBranchRepository;
import repositories.UserRepository;
import security.PasswordHasher;
import security.UserStatusCache;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserBranchRepository userBranchRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private UserStatusCache userStatusCache;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuditService auditService;
    @Mock private BranchLookupClient branchLookupClient;

    private UserService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Database database = mock(Database.class, invocation -> {
            if ("withTransaction".equals(invocation.getMethod().getName())
                    && invocation.getArgument(0) instanceof ConnectionCallable<?>) {
                ConnectionCallable<?> transaction = invocation.getArgument(0);
                return transaction.call(mock(Connection.class));
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        when(branchLookupClient.getAllActiveBranchIds())
                .thenReturn(CompletableFuture.completedFuture(List.of(1, 2)));
        service = new UserService(
                userRepository,
                roleRepository,
                userBranchRepository,
                refreshTokenRepository,
                passwordHasher,
                userStatusCache,
                refreshTokenService,
                auditService,
                database,
                Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC),
                branchLookupClient,
                passwordConfig());
    }

    @Test
    void createUser_validRequest_persistsUserAndReturnsResponse() {
        Role role = new Role(3, "SP", "Pharmacist", true);
        when(userRepository.existsUsername("new.user")).thenReturn(false);
        when(userRepository.existsEmail("new@example.local")).thenReturn(false);
        when(roleRepository.getById(3)).thenReturn(Optional.of(role));
        when(passwordHasher.hash("Strong@Pass1")).thenReturn("password-hash");
        when(userRepository.create(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(22);
            return 22;
        });

        UserResponse response = service.createUser(createRequest(), 1, "127.0.0.1");

        assertThat(response.getUserId()).isEqualTo(22);
        assertThat(response.getRoleCode()).isEqualTo("SP");
        assertThat(response.getBranchIds()).containsExactly(2);
        verify(userBranchRepository).replaceBranches(22, List.of(2));
        verify(auditService).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    void createUser_duplicateUsername_returnsConflict() {
        when(userRepository.existsUsername("new.user")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(createRequest(), 1, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(409));
        verify(userRepository, never()).create(any(User.class));
    }

    @Test
    void createUser_invalidPassword_returnsBadRequest() {
        CreateUserRequest request = createRequest();
        request.setPassword("weak");

        assertThatThrownBy(() -> service.createUser(request, 1, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(400));
    }

    @Test
    void listUsers_activeFilterAndPagination_returnsMappedPage() {
        User user = user(8, 3);
        when(userRepository.search("ph", null, 3, true, false, 1, 100, "FullName", "ASC"))
                .thenReturn(List.of(user));
        when(userRepository.count("ph", null, 3, true, false)).thenReturn(1L);
        when(roleRepository.getById(3)).thenReturn(Optional.of(new Role(3, "SP", "Pharmacist", true)));
        when(userBranchRepository.getBranchIdsByUserId(8)).thenReturn(List.of(2));

        var result = service.listUsers(
                "ph", null, 3, "active", 0, 250, "fullName", "ASC");

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(100);
        assertThat(result.getTotalItems()).isEqualTo(1);
    }

    @Test
    void getUser_existingUserWithoutRole_returnsUserDetails() {
        when(userRepository.getById(8)).thenReturn(Optional.of(user(8, 3)));
        when(roleRepository.getById(3)).thenReturn(Optional.empty());
        when(userBranchRepository.getBranchIdsByUserId(8)).thenReturn(List.of(1));

        UserResponse response = service.getUser(8);

        assertThat(response.getUsername()).isEqualTo("user8");
        assertThat(response.getRoleCode()).isNull();
    }

    @Test
    void updateUser_validRequest_updatesProfileAndAudits() {
        User user = user(8, 3);
        when(userRepository.getById(8)).thenReturn(Optional.of(user));
        when(roleRepository.getById(3)).thenReturn(Optional.of(new Role(3, "SP", "Pharmacist", true)));
        when(userBranchRepository.getBranchIdsByUserId(8)).thenReturn(List.of(2));
        UpdateUserRequest request = updateRequest(3);

        UserResponse response = service.updateUser(8, request, 1, "127.0.0.1");

        assertThat(response.getFullName()).isEqualTo("Updated User");
        verify(userRepository).update(user);
        verify(auditService).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateRoleAndBranches_validRequest_updatesAndRevokesSessions() {
        User user = user(8, 3);
        when(userRepository.getById(8)).thenReturn(Optional.of(user));
        when(roleRepository.getById(2)).thenReturn(Optional.of(new Role(2, "WH", "Warehouse", true)));
        UpdateRoleBranchesRequest request = new UpdateRoleBranchesRequest();
        request.setRoleId(2);
        request.setBranchIds(List.of(1, 2));

        UserResponse response = service.updateRoleAndBranches(8, request, 1, "127.0.0.1");

        assertThat(response.getRoleCode()).isEqualTo("WH");
        assertThat(response.getBranchIds()).containsExactly(1, 2);
        verify(userRepository).update(user);
        verify(userBranchRepository).replaceBranches(8, List.of(1, 2));
        verify(refreshTokenRepository).revokeAllByUserId(8);
        verify(userStatusCache).evict(8);
    }

    @Test
    void lockUser_otherUser_revokesSessionsAndClearsCache() {
        when(userRepository.getById(8)).thenReturn(Optional.of(user(8, 3)));
        when(roleRepository.getById(3)).thenReturn(Optional.of(new Role(3, "SP", "Pharmacist", true)));

        service.lockUser(8, 1, "127.0.0.1");

        verify(userRepository).setLocked(8, true);
        verify(refreshTokenRepository).revokeAllByUserId(8);
        verify(userStatusCache).evict(8);
    }

    @Test
    void lockUser_adminLockingSelf_isForbidden() {
        assertThatThrownBy(() -> service.lockUser(1, 1, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(403));
    }

    @Test
    void unlockDeleteActivateAndResetPassword_applyAccountChanges() {
        User user = user(8, 3);
        when(userRepository.getById(8)).thenReturn(Optional.of(user));
        when(passwordHasher.hash("NewPass@123")).thenReturn("new-hash");

        service.unlockUser(8, 1, "127.0.0.1");
        service.deleteUser(8, 1, "127.0.0.1");
        service.activateUser(8, 1, "127.0.0.1");
        service.resetPassword(8, "NewPass@123", 1, "127.0.0.1");

        verify(userRepository).setLocked(8, false);
        verify(userRepository).resetFailedLogin(8);
        verify(userRepository).setActive(8, false);
        verify(userRepository).setActive(8, true);
        verify(userRepository).updatePasswordHash(8, "new-hash");
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).revokeAllByUserId(8);
    }

    private User user(Integer userId, Integer roleId) {
        User user = new User();
        user.setUserId(userId);
        user.setRoleId(roleId);
        user.setUsername("user" + userId);
        user.setPasswordHash("old-hash");
        user.setFullName("Existing User");
        user.setEmail("user" + userId + "@example.local");
        user.setPhone("0900000000");
        user.setActive(true);
        return user;
    }

    private CreateUserRequest createRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(" new.user ");
        request.setPassword("Strong@Pass1");
        request.setFullName(" New User ");
        request.setEmail("new@example.local");
        request.setPhone("0900000000");
        request.setRoleId(3);
        request.setBranchIds(List.of(2));
        return request;
    }

    private UpdateUserRequest updateRequest(Integer roleId) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFullName("Updated User");
        request.setEmail("user8@example.local");
        request.setPhone("0900000001");
        request.setRoleId(roleId);
        return request;
    }

    private Config passwordConfig() {
        return ConfigFactory.parseString(
                "password.policy.minLength=8\n"
                        + "password.policy.requireUppercase=true\n"
                        + "password.policy.requireLowercase=true\n"
                        + "password.policy.requireDigit=true\n"
                        + "password.policy.requireSpecialChar=true");
    }
}

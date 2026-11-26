package services;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import clients.BranchLookupClient;
import domain.Role;
import dto.CreateUserRequest;
import dto.UpdateRoleBranchesRequest;
import errors.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import play.db.Database;
import repositories.*;
import security.PasswordHasher;
import security.UserStatusCache;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserServiceBranchValidationTest {
    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private BranchLookupClient branchLookupClient;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        branchLookupClient = mock(BranchLookupClient.class);
        when(branchLookupClient.getAllActiveBranchIds())
                .thenReturn(CompletableFuture.completedFuture(List.of(1, 2, 3)));

        userService = new UserService(
                userRepository,
                roleRepository,
                mock(UserBranchRepository.class),
                mock(RefreshTokenRepository.class),
                mock(PasswordHasher.class),
                mock(UserStatusCache.class),
                mock(RefreshTokenService.class),
                mock(AuditService.class),
                mock(Database.class),
                Clock.fixed(java.time.Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC),
                branchLookupClient,
                passwordConfig());
    }

    @Test
    void createUser_withInactiveBranch_shouldRejectBeforeDatabaseWrite() {
        when(userRepository.existsUsername("new.user")).thenReturn(false);
        when(userRepository.existsEmail("new@example.local")).thenReturn(false);
        when(roleRepository.getById(2)).thenReturn(Optional.of(new Role(2, "PH", "Pharmacist", true)));

        assertThatThrownBy(() -> userService.createUser(
                createRequest("PH", List.of(1, 999)), 1, "127.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        org.assertj.core.api.Assertions.assertThat(error.getCode())
                                .isEqualTo(domain.ErrorCodes.BRANCH_INACTIVE));
    }

    @Test
    void createUser_withAdminMissingActiveBranch_shouldReject() {
        when(userRepository.existsUsername("new.user")).thenReturn(false);
        when(userRepository.existsEmail("new@example.local")).thenReturn(false);
        when(roleRepository.getById(1)).thenReturn(Optional.of(new Role(1, "AD", "Admin", true)));

        assertThatThrownBy(() -> userService.createUser(
                createRequest("AD", List.of(1, 2)), 1, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo(domain.ErrorCodes.BRANCH_NOT_ACCESSIBLE);
    }

    @Test
    void updateRoleAndBranches_withEmptyBranchList_shouldReject() {
        domain.User user = new domain.User();
        user.setUserId(7);
        user.setRoleId(2);
        when(userRepository.getById(7)).thenReturn(Optional.of(user));
        when(roleRepository.getById(2)).thenReturn(Optional.of(new Role(2, "PH", "Pharmacist", true)));

        UpdateRoleBranchesRequest request = new UpdateRoleBranchesRequest();
        request.setRoleId(2);
        request.setBranchIds(List.of());

        assertThatThrownBy(() -> userService.updateRoleAndBranches(7, request, 1, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo(domain.ErrorCodes.VALIDATION_FAILED);
        verify(branchLookupClient, never()).getAllActiveBranchIds();
    }

    private CreateUserRequest createRequest(String roleCode, List<Integer> branchIds) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("new.user");
        request.setPassword("Good@Pass1");
        request.setFullName("New User");
        request.setEmail("new@example.local");
        request.setRoleId("AD".equals(roleCode) ? 1 : 2);
        request.setBranchIds(branchIds);
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

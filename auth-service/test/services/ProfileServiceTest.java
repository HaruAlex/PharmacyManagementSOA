package services;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import domain.Role;
import domain.User;
import dto.ChangePasswordRequest;
import dto.ProfileResponse;
import dto.UpdateProfileRequest;
import errors.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import repositories.RefreshTokenRepository;
import repositories.RoleRepository;
import repositories.UserBranchRepository;
import repositories.UserRepository;
import security.PasswordHasher;
import security.UserStatusCache;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserBranchRepository userBranchRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private UserStatusCache userStatusCache;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuditService auditService;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProfileService(
                userRepository,
                roleRepository,
                userBranchRepository,
                refreshTokenRepository,
                passwordHasher,
                userStatusCache,
                refreshTokenService,
                auditService,
                passwordConfig());
    }

    @Test
    void getProfile_existingUser_returnsProfileWithRoleAndBranches() {
        User user = user();
        Role role = new Role(3, "SP", "Pharmacist", true);
        when(userRepository.getById(7)).thenReturn(Optional.of(user));
        when(roleRepository.getById(3)).thenReturn(Optional.of(role));
        when(userBranchRepository.getBranchIdsByUserId(7)).thenReturn(List.of(1, 2));

        ProfileResponse response = service.getProfile(7);

        assertThat(response.getUsername()).isEqualTo("pharmacist");
        assertThat(response.getRoleCode()).isEqualTo("SP");
        assertThat(response.getBranchIds()).containsExactly(1, 2);
    }

    @Test
    void getProfile_missingUser_returnsNotFound() {
        when(userRepository.getById(7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(7))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(404));
    }

    @Test
    void getProfile_missingRole_returnsNotFound() {
        when(userRepository.getById(7)).thenReturn(Optional.of(user()));
        when(roleRepository.getById(3)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(7))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(404));
    }

    @Test
    void updateProfile_validFields_trimsAndAuditsChanges() {
        User user = user();
        when(userRepository.getById(7)).thenReturn(Optional.of(user));
        when(roleRepository.getById(3)).thenReturn(Optional.of(new Role(3, "SP", "Pharmacist", true)));
        when(userBranchRepository.getBranchIdsByUserId(7)).thenReturn(List.of(2));
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("  Updated User  ");
        request.setEmail(" ph.user@example.local ");
        request.setPhone(" 0900000000 ");

        ProfileResponse response = service.updateProfile(7, request, "127.0.0.1");

        assertThat(user.getFullName()).isEqualTo("Updated User");
        assertThat(user.getEmail()).isEqualTo("ph.user@example.local");
        assertThat(user.getPhone()).isEqualTo("0900000000");
        assertThat(response.getFullName()).isEqualTo("Updated User");
        verify(userRepository).update(user);
        verify(auditService).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateProfile_duplicateEmail_returnsConflict() {
        User user = user();
        when(userRepository.getById(7)).thenReturn(Optional.of(user));
        when(userRepository.existsEmail("other@example.local")).thenReturn(true);
        UpdateProfileRequest request = profileRequest("other@example.local");

        assertThatThrownBy(() -> service.updateProfile(7, request, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(409));
        verify(userRepository, never()).update(any(User.class));
    }

    @Test
    void updateProfile_missingRequiredField_returnsBadRequest() {
        when(userRepository.getById(7)).thenReturn(Optional.of(user()));
        UpdateProfileRequest request = profileRequest("ph.user@example.local");
        request.setFullName(" ");

        assertThatThrownBy(() -> service.updateProfile(7, request, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(400));
    }

    @Test
    void changePassword_validNewPasswordUpdatesHashAndRevokesSessions() {
        User user = user();
        when(userRepository.getById(7)).thenReturn(Optional.of(user));
        when(passwordHasher.verify("Current@123", "old-hash")).thenReturn(true);
        when(passwordHasher.verify("NewPass@456", "old-hash")).thenReturn(false);
        when(passwordHasher.hash("NewPass@456")).thenReturn("new-hash");

        service.changePassword(7, passwordRequest("Current@123", "NewPass@456", "NewPass@456"), "127.0.0.1");

        verify(userRepository).updatePasswordHash(7, "new-hash");
        verify(refreshTokenService).revokeAllTokens(7, "127.0.0.1");
        verify(userStatusCache).evict(7);
        verify(auditService).write(any(), any(), any(), any(), any(), any());
    }

    @Test
    void changePassword_wrongCurrentPassword_returnsUnauthorized() {
        when(userRepository.getById(7)).thenReturn(Optional.of(user()));
        when(passwordHasher.verify("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(
                7, passwordRequest("wrong", "NewPass@456", "NewPass@456"), "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(401));
    }

    @Test
    void changePassword_mismatchedConfirmation_returnsBadRequest() {
        when(userRepository.getById(7)).thenReturn(Optional.of(user()));
        when(passwordHasher.verify("Current@123", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(
                7, passwordRequest("Current@123", "NewPass@456", "Different@456"), "127.0.0.1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void changePassword_samePassword_returnsBadRequest() {
        when(userRepository.getById(7)).thenReturn(Optional.of(user()));
        when(passwordHasher.verify("Current@123", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(
                7, passwordRequest("Current@123", "Current@123", "Current@123"), "127.0.0.1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void changePassword_eachPolicyViolation_isRejected() {
        when(userRepository.getById(7)).thenReturn(Optional.of(user()));
        when(passwordHasher.verify("Current@123", "old-hash")).thenReturn(true);
        when(passwordHasher.verify(any(), org.mockito.ArgumentMatchers.eq("old-hash"))).thenReturn(false);

        for (String invalidPassword : List.of(
                "short1!",
                "lowercase1!",
                "UPPERCASE1!",
                "NoDigits!!",
                "NoSpecial123")) {
            assertThatThrownBy(() -> service.changePassword(
                    7,
                    passwordRequest("Current@123", invalidPassword, invalidPassword),
                    "127.0.0.1"))
                    .isInstanceOf(ApiException.class);
        }

        verify(userRepository, never()).updatePasswordHash(any(), any());
    }

    private User user() {
        User user = new User();
        user.setUserId(7);
        user.setRoleId(3);
        user.setUsername("pharmacist");
        user.setPasswordHash("old-hash");
        user.setFullName("Pharmacist User");
        user.setEmail("ph.user@example.local");
        user.setPhone("0900000000");
        user.setActive(true);
        return user;
    }

    private UpdateProfileRequest profileRequest(String email) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated User");
        request.setEmail(email);
        return request;
    }

    private ChangePasswordRequest passwordRequest(String current, String next, String confirm) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        request.setConfirmNewPassword(confirm);
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

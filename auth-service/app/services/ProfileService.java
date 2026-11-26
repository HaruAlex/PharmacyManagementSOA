package services;

import domain.*;
import dto.ChangePasswordRequest;
import dto.ProfileResponse;
import dto.UpdateProfileRequest;
import errors.ApiException;
import repositories.*;
import security.PasswordHasher;
import security.UserStatusCache;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Service xử lý profile và đổi mật khẩu của user
 */
@Singleton
public class ProfileService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserBranchRepository userBranchRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final UserStatusCache userStatusCache;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    
    // Password policy (giống UserService)
    private final int passwordMinLength;
    private final boolean passwordRequireUppercase;
    private final boolean passwordRequireLowercase;
    private final boolean passwordRequireDigit;
    private final boolean passwordRequireSpecialChar;

    @Inject
    public ProfileService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserBranchRepository userBranchRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher,
            UserStatusCache userStatusCache,
            RefreshTokenService refreshTokenService,
            AuditService auditService,
            com.typesafe.config.Config config) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userBranchRepository = userBranchRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.userStatusCache = userStatusCache;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        
        this.passwordMinLength = config.getInt("password.policy.minLength");
        this.passwordRequireUppercase = config.getBoolean("password.policy.requireUppercase");
        this.passwordRequireLowercase = config.getBoolean("password.policy.requireLowercase");
        this.passwordRequireDigit = config.getBoolean("password.policy.requireDigit");
        this.passwordRequireSpecialChar = config.getBoolean("password.policy.requireSpecialChar");
    }

    /**
     * Lấy thông tin profile của user hiện tại
     */
    public ProfileResponse getProfile(Integer userId) {
        User user = userRepository.getById(userId)
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
        
        Role role = roleRepository.getById(user.getRoleId())
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.ROLE_NOT_FOUND, "Role not found"));
        
        List<Integer> branchIds = userBranchRepository.getBranchIdsByUserId(userId);
        
        return buildProfileResponse(user, role, branchIds);
    }

    /**
     * Cập nhật profile của user hiện tại
     * Chỉ được đổi fullName, email, phone
     * KHÔNG được đổi username, roleId, branchIds
     */
    public ProfileResponse updateProfile(Integer userId, UpdateProfileRequest request, String ipAddress) {
        User user = userRepository.getById(userId)
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
        
        // Kiểm tra dữ liệu đầu vào
        if (request.getFullName() == null || request.getFullName().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Full name is required");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Email is required");
        }
        
        // Kiểm tra email trùng với user khác
        String emailLower = request.getEmail().trim().toLowerCase();
        if (!emailLower.equals(user.getEmail().toLowerCase())) {
            if (userRepository.existsEmail(emailLower)) {
                throw ApiException.conflict(ErrorCodes.USER_EMAIL_EXISTS, "Email already exists");
            }
        }
        
        // Cập nhật chỉ các field được phép
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail().trim());
        user.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        
        userRepository.update(user);
        
        auditService.write(userId, null, AuditActions.PROFILE_UPDATED, "User", userId.longValue(),
                String.format("Profile updated, IP: %s", ipAddress));
        
        // Lấy lại thông tin mới
        return getProfile(userId);
    }

    /**
     * Đổi mật khẩu
     */
    public void changePassword(Integer userId, ChangePasswordRequest request, String ipAddress) {
        User user = userRepository.getById(userId)
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
        
        // Kiểm tra dữ liệu đầu vào
        if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Current password is required");
        }
        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "New password is required");
        }
        if (request.getConfirmNewPassword() == null || request.getConfirmNewPassword().isBlank()) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED, "Confirm new password is required");
        }
        
        // Kiểm tra mật khẩu hiện tại
        if (!passwordHasher.verify(request.getCurrentPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized(ErrorCodes.USER_CURRENT_PASSWORD_INCORRECT, 
                "Current password is incorrect");
        }
        
        // Kiểm tra confirm password khớp
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw ApiException.badRequest(ErrorCodes.USER_PASSWORD_CONFIRM_MISMATCH, 
                "New password and confirmation do not match");
        }
        
        // Kiểm tra mật khẩu mới khác mật khẩu cũ
        if (passwordHasher.verify(request.getNewPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest(ErrorCodes.USER_NEW_PASSWORD_SAME_AS_OLD, 
                "New password must be different from current password");
        }
        
        // Kiểm tra chính sách mật khẩu
        validatePassword(request.getNewPassword());
        
        // Hash mật khẩu mới
        String newPasswordHash = passwordHasher.hash(request.getNewPassword());
        
        // Cập nhật password hash
        userRepository.updatePasswordHash(userId, newPasswordHash);
        
        // Revoke tất cả refresh tokens của user (theo assumptions: revoke tất cả)
        refreshTokenService.revokeAllTokens(userId, ipAddress);
        
        // Xóa cache trạng thái user
        userStatusCache.evict(userId);
        
        auditService.write(userId, null, AuditActions.PASSWORD_CHANGED, "User", userId.longValue(),
                String.format("Password changed by user, IP: %s", ipAddress));
    }

    /**
     * Kiểm tra chính sách mật khẩu
     */
    private void validatePassword(String password) {
        if (password.length() < passwordMinLength) {
            throw ApiException.badRequest(ErrorCodes.PASSWORD_WEAK, 
                String.format("Password must be at least %d characters long", passwordMinLength));
        }
        
        if (passwordRequireUppercase && !password.matches(".*[A-Z].*")) {
            throw ApiException.badRequest(ErrorCodes.PASSWORD_WEAK, 
                "Password must contain at least one uppercase letter");
        }
        
        if (passwordRequireLowercase && !password.matches(".*[a-z].*")) {
            throw ApiException.badRequest(ErrorCodes.PASSWORD_WEAK, 
                "Password must contain at least one lowercase letter");
        }
        
        if (passwordRequireDigit && !password.matches(".*\\d.*")) {
            throw ApiException.badRequest(ErrorCodes.PASSWORD_WEAK, 
                "Password must contain at least one digit");
        }
        
        if (passwordRequireSpecialChar && !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            throw ApiException.badRequest(ErrorCodes.PASSWORD_WEAK, 
                "Password must contain at least one special character");
        }
    }

    /**
     * Tạo ProfileResponse từ User, Role và BranchIds
     */
    private ProfileResponse buildProfileResponse(User user, Role role, List<Integer> branchIds) {
        ProfileResponse response = new ProfileResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setRoleId(role.getRoleId());
        response.setRoleCode(role.getRoleCode());
        response.setRoleName(role.getRoleName());
        response.setBranchIds(branchIds);
        response.setIsActive(user.isActive());
        response.setIsLocked(user.isLocked());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }
}

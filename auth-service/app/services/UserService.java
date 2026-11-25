package services;

import com.typesafe.config.Config;
import domain.*;
import dto.*;
import errors.ApiException;
import play.db.Database;
import repositories.*;
import security.PasswordHasher;
import security.UserStatusCache;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service xử lý quản lý user (chỉ dành cho Admin)
 */
@Singleton
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserBranchRepository userBranchRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordHasher passwordHasher;
    private final UserStatusCache userStatusCache;
    private final RefreshTokenService refreshTokenService;
    private final Database database;
    private final Clock clock;
    
    // Password policy từ config
    private final int passwordMinLength;
    private final boolean passwordRequireUppercase;
    private final boolean passwordRequireLowercase;
    private final boolean passwordRequireDigit;
    private final boolean passwordRequireSpecialChar;

    @Inject
    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserBranchRepository userBranchRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuditLogRepository auditLogRepository,
            PasswordHasher passwordHasher,
            UserStatusCache userStatusCache,
            RefreshTokenService refreshTokenService,
            Database database,
            Clock clock,
            Config config) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userBranchRepository = userBranchRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordHasher = passwordHasher;
        this.userStatusCache = userStatusCache;
        this.refreshTokenService = refreshTokenService;
        this.database = database;
        this.clock = clock;
        
        this.passwordMinLength = config.getInt("password.policy.minLength");
        this.passwordRequireUppercase = config.getBoolean("password.policy.requireUppercase");
        this.passwordRequireLowercase = config.getBoolean("password.policy.requireLowercase");
        this.passwordRequireDigit = config.getBoolean("password.policy.requireDigit");
        this.passwordRequireSpecialChar = config.getBoolean("password.policy.requireSpecialChar");
    }

    /**
     * Tạo user mới (chỉ Admin)
     */
    public UserResponse createUser(CreateUserRequest request, Integer adminUserId, String ipAddress) {
        // Validate password policy
        validatePassword(request.getPassword());
        
        // Kiểm tra username đã tồn tại (case-insensitive)
        String usernameLower = request.getUsername().trim().toLowerCase();
        if (userRepository.existsUsername(usernameLower)) {
            throw ApiException.conflict(ErrorCodes.USER_USERNAME_EXISTS, 
                "Username already exists");
        }
        
        // Kiểm tra email đã tồn tại
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String emailLower = request.getEmail().trim().toLowerCase();
            if (userRepository.existsEmail(emailLower)) {
                throw ApiException.conflict(ErrorCodes.USER_EMAIL_EXISTS, 
                    "Email already exists");
            }
        }
        
        // Kiểm tra role tồn tại và active
        Role role = roleRepository.getById(request.getRoleId())
            .orElseThrow(() -> ApiException.badRequest(ErrorCodes.ROLE_NOT_FOUND, 
                "Role not found"));
        
        if (!role.isActive()) {
            throw ApiException.badRequest(ErrorCodes.ROLE_INACTIVE, 
                "Role is inactive");
        }
        
        // TODO: Kiểm tra branches hợp lệ qua BranchLookupClient (Phase 4 skip for now)
        
        Instant now = clock.instant();
        
        // Tạo user trong transaction
        return database.withTransaction(conn -> {
            // Tạo user
            User user = new User();
            user.setUsername(request.getUsername().trim());
            user.setPasswordHash(passwordHasher.hash(request.getPassword()));
            user.setFullName(request.getFullName().trim());
            user.setEmail(request.getEmail() != null ? request.getEmail().trim() : null);
            user.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
            user.setRoleId(request.getRoleId());
            user.setActive(true);
            user.setLocked(false);
            user.setFailedLoginCount(0);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            
            Integer userId;
            try {
                userId = userRepository.create(user);
            } catch (Exception e) {
                // Xử lý race condition: unique constraint violation
                // SQLException có thể được wrap trong RuntimeException
                String message = e.getMessage();
                if (message != null && (message.contains("2627") || message.contains("2601") || 
                    message.contains("unique") || message.contains("UNIQUE"))) {
                    throw ApiException.conflict(ErrorCodes.USER_USERNAME_EXISTS, 
                        "Username or email already exists");
                }
                throw new RuntimeException("Failed to create user", e);
            }
            
            user.setUserId(userId);
            
            // Gán branches
            userBranchRepository.replaceBranches(userId, request.getBranchIds());
            
            // Ghi audit log
            writeAudit(adminUserId, null, AuditActions.USER_CREATED, "User", userId.longValue(),
                String.format("User %s created with role %s, IP: %s", 
                    user.getUsername(), role.getRoleCode(), ipAddress));
            
            // Trả về response
            return buildUserResponse(user, role, request.getBranchIds());
        });
    }

    /**
     * Lấy danh sách user (phân trang, lọc)
     */
    public PagedResult<UserResponse> listUsers(
            String name, 
            String email, 
            Integer roleId, 
            String status,
            int page, 
            int pageSize,
            String sortBy,
            String sortOrder) {
        
        // Validate page size (tối đa 100)
        if (pageSize > 100) {
            pageSize = 100;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        if (page < 1) {
            page = 1;
        }
        
        // Validate sort field (whitelist)
        List<String> allowedSortFields = List.of("userId", "username", "fullName", "email", "createdAt", "updatedAt");
        if (sortBy == null || !allowedSortFields.contains(sortBy)) {
            sortBy = "createdAt";
        }
        
        // Validate sort order
        if (!"ASC".equalsIgnoreCase(sortOrder) && !"DESC".equalsIgnoreCase(sortOrder)) {
            sortOrder = "DESC";
        }
        
        // Parse status filter
        Boolean isActive = null;
        Boolean isLocked = null;
        if (status != null) {
            switch (status.toLowerCase()) {
                case "active":
                    isActive = true;
                    isLocked = false;
                    break;
                case "locked":
                    isLocked = true;
                    break;
                case "inactive":
                    isActive = false;
                    break;
            }
        }
        
        // Search users
        List<User> users = userRepository.search(
            name, email, roleId, isActive, isLocked,
            page, pageSize, sortBy, sortOrder
        );
        
        // Count total
        long totalItems = userRepository.count(name, email, roleId, isActive, isLocked);
        
        // Build response
        List<UserResponse> userResponses = users.stream().map(user -> {
            Role role = roleRepository.getById(user.getRoleId()).orElse(null);
            List<Integer> branchIds = userBranchRepository.getBranchIdsByUserId(user.getUserId());
            return buildUserResponse(user, role, branchIds);
        }).toList();
        
        return new PagedResult<>(userResponses, page, pageSize, totalItems);
    }

    /**
     * Lấy chi tiết user
     */
    public UserResponse getUser(Integer userId) {
        User user = userRepository.getById(userId)
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
        
        Role role = roleRepository.getById(user.getRoleId()).orElse(null);
        List<Integer> branchIds = userBranchRepository.getBranchIdsByUserId(userId);
        
        return buildUserResponse(user, role, branchIds);
    }

    /**
     * Cập nhật user (không được đổi username)
     */
    public UserResponse updateUser(Integer userId, UpdateUserRequest request, Integer adminUserId, String ipAddress) {
        User user = userRepository.getById(userId)
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
        
        // Kiểm tra email trùng với user khác
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String emailLower = request.getEmail().trim().toLowerCase();
            if (!emailLower.equals(user.getEmail().toLowerCase())) {
                if (userRepository.existsEmail(emailLower)) {
                    throw ApiException.conflict(ErrorCodes.USER_EMAIL_EXISTS, "Email already exists");
                }
            }
        }
        
        // Kiểm tra role tồn tại
        Role role = roleRepository.getById(request.getRoleId())
            .orElseThrow(() -> ApiException.badRequest(ErrorCodes.ROLE_NOT_FOUND, "Role not found"));
        
        if (!role.isActive()) {
            throw ApiException.badRequest(ErrorCodes.ROLE_INACTIVE, "Role is inactive");
        }
        
        // Cập nhật user
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail() != null ? request.getEmail().trim() : null);
        user.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        user.setRoleId(request.getRoleId());
        user.setUpdatedAt(clock.instant());
        
        userRepository.update(user);
        
        // Ghi audit
        writeAudit(adminUserId, null, AuditActions.USER_UPDATED, "User", userId.longValue(),
            String.format("User %s updated, IP: %s", user.getUsername(), ipAddress));
        
        List<Integer> branchIds = userBranchRepository.getBranchIdsByUserId(userId);
        return buildUserResponse(user, role, branchIds);
    }

    /**
     * Gán role + branches cho user (revoke tokens, clear cache)
     */
    public UserResponse updateRoleAndBranches(Integer userId, UpdateRoleBranchesRequest request, 
                                              Integer adminUserId, String ipAddress) {
        User user = userRepository.getById(userId)
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
        
        // Kiểm tra role tồn tại
        Role role = roleRepository.getById(request.getRoleId())
            .orElseThrow(() -> ApiException.badRequest(ErrorCodes.ROLE_NOT_FOUND, "Role not found"));
        
        if (!role.isActive()) {
            throw ApiException.badRequest(ErrorCodes.ROLE_INACTIVE, "Role is inactive");
        }
        
        // TODO: Validate branches qua BranchLookupClient
        
        // Cập nhật trong transaction
        return database.withTransaction(conn -> {
            // Update role
            user.setRoleId(request.getRoleId());
            user.setUpdatedAt(clock.instant());
            userRepository.update(user);
            
            // Replace branches
            userBranchRepository.replaceBranches(userId, request.getBranchIds());
            
            // Revoke all refresh tokens
            refreshTokenRepository.revokeAllByUserId(userId);
            
            // Clear user status cache
            userStatusCache.evict(userId);
            
            // Audit
            writeAudit(adminUserId, null, AuditActions.USER_ROLE_CHANGED, "User", userId.longValue(),
                String.format("User %s role changed to %s, IP: %s", user.getUsername(), role.getRoleCode(), ipAddress));
            
            writeAudit(adminUserId, null, AuditActions.USER_BRANCHES_CHANGED, "User", userId.longValue(),
                String.format("User %s branches changed, IP: %s", user.getUsername(), ipAddress));
            
            return buildUserResponse(user, role, request.getBranchIds());
        });
    }

    /**
     * Khóa user
     */
    public void lockUser(Integer userId, Integer adminUserId, String ipAddress) {
        // Không cho admin tự khóa chính mình
        if (userId.equals(adminUserId)) {
            throw ApiException.forbidden(ErrorCodes.CANNOT_LOCK_SELF, 
                "Cannot lock your own account");
        }
        
        User user = userRepository.getById(userId)
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
        
        // Kiểm tra không khóa Admin cuối cùng
        Role role = roleRepository.getById(user.getRoleId()).orElse(null);
        if (role != null && "AD".equals(role.getRoleCode())) {
            long activeAdminCount = userRepository.countActiveAdmins();
            if (activeAdminCount <= 1) {
                throw ApiException.forbidden(ErrorCodes.CANNOT_LOCK_LAST_ADMIN, 
                    "Cannot lock the last active admin");
            }
        }
        
        // Lock user
        userRepository.setLocked(userId, true);
        
        // Revoke refresh tokens
        refreshTokenRepository.revokeAllByUserId(userId);
        
        // Clear cache
        userStatusCache.evict(userId);
        
        // Audit
        writeAudit(adminUserId, null, AuditActions.USER_LOCKED, "User", userId.longValue(),
            String.format("User %s locked by admin, IP: %s", user.getUsername(), ipAddress));
    }

    /**
     * Mở khóa user (reset failed login count)
     */
    public void unlockUser(Integer userId, Integer adminUserId, String ipAddress) {
        User user = userRepository.getById(userId)
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
        
        // Unlock user
        userRepository.setLocked(userId, false);
        
        // Reset failed login count
        userRepository.resetFailedLogin(userId);
        
        // Clear cache
        userStatusCache.evict(userId);
        
        // Audit
        writeAudit(adminUserId, null, AuditActions.USER_UNLOCKED, "User", userId.longValue(),
            String.format("User %s unlocked by admin, IP: %s", user.getUsername(), ipAddress));
    }

    /**
     * Xóa mềm user (IsActive=0)
     */
    public void deleteUser(Integer userId, Integer adminUserId, String ipAddress) {
        // Không cho admin tự xóa chính mình
        if (userId.equals(adminUserId)) {
            throw ApiException.forbidden(ErrorCodes.CANNOT_DELETE_SELF, 
                "Cannot delete your own account");
        }
        
        User user = userRepository.getById(userId)
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
        
        // Kiểm tra không xóa Admin cuối cùng
        Role role = roleRepository.getById(user.getRoleId()).orElse(null);
        if (role != null && "AD".equals(role.getRoleCode())) {
            long activeAdminCount = userRepository.countActiveAdmins();
            if (activeAdminCount <= 1) {
                throw ApiException.forbidden(ErrorCodes.CANNOT_DELETE_LAST_ADMIN, 
                    "Cannot delete the last active admin");
            }
        }
        
        // Xóa mềm
        userRepository.setActive(userId, false);
        
        // Revoke refresh tokens
        refreshTokenRepository.revokeAllByUserId(userId);
        
        // Clear cache
        userStatusCache.evict(userId);
        
        // Audit
        writeAudit(adminUserId, null, AuditActions.USER_DELETED, "User", userId.longValue(),
            String.format("User %s soft-deleted by admin, IP: %s", user.getUsername(), ipAddress));
    }

    /**
     * Kích hoạt lại user
     */
    public void activateUser(Integer userId, Integer adminUserId, String ipAddress) {
        User user = userRepository.getById(userId)
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
        
        // Activate user
        userRepository.setActive(userId, true);
        
        // Clear cache
        userStatusCache.evict(userId);
        
        // Audit
        writeAudit(adminUserId, null, AuditActions.USER_ACTIVATED, "User", userId.longValue(),
            String.format("User %s activated by admin, IP: %s", user.getUsername(), ipAddress));
    }

    /**
     * Reset mật khẩu user (admin)
     */
    public void resetPassword(Integer userId, String newPassword, Integer adminUserId, String ipAddress) {
        // Validate password policy
        validatePassword(newPassword);
        
        User user = userRepository.getById(userId)
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
        
        // Update password hash
        String newHash = passwordHasher.hash(newPassword);
        userRepository.updatePasswordHash(userId, newHash);
        
        // Revoke refresh tokens
        refreshTokenRepository.revokeAllByUserId(userId);
        
        // Clear cache
        userStatusCache.evict(userId);
        
        // Audit (không ghi mật khẩu)
        writeAudit(adminUserId, null, AuditActions.PASSWORD_RESET, "User", userId.longValue(),
            String.format("Password reset for user %s by admin, IP: %s", user.getUsername(), ipAddress));
    }

    /**
     * Validate password policy
     */
    private void validatePassword(String password) {
        if (password == null || password.length() < passwordMinLength) {
            throw ApiException.badRequest(ErrorCodes.PASSWORD_WEAK, 
                String.format("Password must be at least %d characters", passwordMinLength));
        }
        
        if (passwordRequireUppercase && !Pattern.compile("[A-Z]").matcher(password).find()) {
            throw ApiException.badRequest(ErrorCodes.PASSWORD_WEAK, 
                "Password must contain at least one uppercase letter");
        }
        
        if (passwordRequireLowercase && !Pattern.compile("[a-z]").matcher(password).find()) {
            throw ApiException.badRequest(ErrorCodes.PASSWORD_WEAK, 
                "Password must contain at least one lowercase letter");
        }
        
        if (passwordRequireDigit && !Pattern.compile("[0-9]").matcher(password).find()) {
            throw ApiException.badRequest(ErrorCodes.PASSWORD_WEAK, 
                "Password must contain at least one digit");
        }
        
        if (passwordRequireSpecialChar && !Pattern.compile("[^A-Za-z0-9]").matcher(password).find()) {
            throw ApiException.badRequest(ErrorCodes.PASSWORD_WEAK, 
                "Password must contain at least one special character");
        }
    }

    /**
     * Build UserResponse từ User entity
     */
    private UserResponse buildUserResponse(User user, Role role, List<Integer> branchIds) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setRoleId(user.getRoleId());
        response.setRoleCode(role != null ? role.getRoleCode() : null);
        response.setRoleName(role != null ? role.getRoleName() : null);
        response.setBranchIds(branchIds);
        response.setIsLocked(user.isLocked());
        response.setIsActive(user.isActive());
        response.setFailedLoginCount(user.getFailedLoginCount());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }

    /**
     * Ghi audit log
     */
    private void writeAudit(Integer userId, Integer branchId, String actionCode, String entityName, Long entityId, String details) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setBranchId(branchId);
            log.setActionCode(actionCode);
            log.setEntityName(entityName);
            log.setEntityId(entityId);
            log.setDetails(details.length() > 1000 ? details.substring(0, 1000) : details);
            log.setCreatedAt(clock.instant());
            
            auditLogRepository.write(log);
        } catch (Exception e) {
            System.err.println("Failed to write audit log: " + e.getMessage());
        }
    }
}

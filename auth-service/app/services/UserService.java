package services;

import clients.BranchLookupClient;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final PasswordHasher passwordHasher;
    private final UserStatusCache userStatusCache;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final Database database;
    private final Clock clock;
    private final BranchLookupClient branchLookupClient;
    
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
            PasswordHasher passwordHasher,
            UserStatusCache userStatusCache,
            RefreshTokenService refreshTokenService,
            AuditService auditService,
            Database database,
            Clock clock,
            BranchLookupClient branchLookupClient,
            Config config) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userBranchRepository = userBranchRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.userStatusCache = userStatusCache;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.database = database;
        this.clock = clock;
        this.branchLookupClient = branchLookupClient;
        
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
        // Kiểm tra chính sách mật khẩu
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
        
        List<Integer> branchIds = validateBranchIds(role, request.getBranchIds());
        
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
            userBranchRepository.replaceBranches(userId, branchIds);
            
            // Ghi audit log
            auditService.write(adminUserId, null, AuditActions.USER_CREATED, "User", userId.longValue(),
                String.format("User %s created with role %s, IP: %s", 
                    user.getUsername(), role.getRoleCode(), ipAddress));
            
            // Trả về response
            return buildUserResponse(user, role, branchIds);
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
        
        // Kiểm tra kích thước trang (tối đa 100)
        if (pageSize > 100) {
            pageSize = 100;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        if (page < 1) {
            page = 1;
        }
        
        // Kiểm tra trường sắp xếp theo danh sách cho phép và ánh xạ sang tên cột
        String sortColumn;
        if (sortBy == null) {
            sortColumn = "CreatedAt";
        } else {
            sortColumn = switch (sortBy.toLowerCase()) {
                case "userid" -> "UserId";
                case "username" -> "Username";
                case "fullname" -> "FullName";
                case "email" -> "Email";
                case "createdat" -> "CreatedAt";
                case "updatedat" -> "UpdatedAt";
                default -> "CreatedAt";
            };
        }
        
        // Kiểm tra chiều sắp xếp
        if (!"ASC".equalsIgnoreCase(sortOrder) && !"DESC".equalsIgnoreCase(sortOrder)) {
            sortOrder = "DESC";
        }
        
        // Đọc bộ lọc trạng thái
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
            page, pageSize, sortColumn, sortOrder
        );
        
        // Count total
        long totalItems = userRepository.count(name, email, roleId, isActive, isLocked);
        
        // Tạo response
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

        List<Integer> branchIds = userBranchRepository.getBranchIdsByUserId(userId);
        if (!Objects.equals(user.getRoleId(), request.getRoleId())) {
            branchIds = validateBranchIds(role, branchIds);
        }
        
        // Cập nhật user
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail() != null ? request.getEmail().trim() : null);
        user.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        user.setRoleId(request.getRoleId());
        user.setUpdatedAt(clock.instant());
        
        userRepository.update(user);
        
        // Ghi audit
        auditService.write(adminUserId, null, AuditActions.USER_UPDATED, "User", userId.longValue(),
            String.format("User %s updated, IP: %s", user.getUsername(), ipAddress));
        
        return buildUserResponse(user, role, branchIds);
    }

    /**
     * Gán role và chi nhánh cho người dùng, đồng thời thu hồi token và xóa cache
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
        
        List<Integer> branchIds = validateBranchIds(role, request.getBranchIds());
        
        // Cập nhật trong transaction
        return database.withTransaction(conn -> {
            // Cập nhật vai trò
            user.setRoleId(request.getRoleId());
            user.setUpdatedAt(clock.instant());
            userRepository.update(user);
            
            // Thay thế danh sách chi nhánh
            userBranchRepository.replaceBranches(userId, branchIds);
            
            // Revoke all refresh tokens
            refreshTokenRepository.revokeAllByUserId(userId);
            
            // Clear user status cache
            userStatusCache.evict(userId);
            
            // Audit
            auditService.write(adminUserId, null, AuditActions.USER_ROLE_CHANGED, "User", userId.longValue(),
                String.format("User %s role changed to %s, IP: %s", user.getUsername(), role.getRoleCode(), ipAddress));
            
            auditService.write(adminUserId, null, AuditActions.USER_BRANCHES_CHANGED, "User", userId.longValue(),
                String.format("User %s branches changed, IP: %s", user.getUsername(), ipAddress));
            
            return buildUserResponse(user, role, branchIds);
        });
    }

    private List<Integer> validateBranchIds(Role role, List<Integer> branchIds) {
        if (branchIds == null || branchIds.isEmpty()
                || branchIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED,
                    "At least one valid branch ID is required");
        }
        Set<Integer> requestedIds = new HashSet<>(branchIds);
        if (requestedIds.size() != branchIds.size()) {
            throw ApiException.badRequest(ErrorCodes.VALIDATION_FAILED,
                    "Branch IDs must not contain duplicates");
        }

        List<Integer> activeBranchIds = branchLookupClient.getAllActiveBranchIds()
                .toCompletableFuture()
                .join();
        if (activeBranchIds == null) {
            throw new IllegalStateException("Branch lookup returned no active branch list");
        }
        Set<Integer> activeIds = new HashSet<>(activeBranchIds);
        if (!activeIds.containsAll(requestedIds)) {
            throw ApiException.badRequest(ErrorCodes.BRANCH_INACTIVE,
                    "One or more branches do not exist or are inactive");
        }
        if ("AD".equals(role.getRoleCode()) && !activeIds.equals(requestedIds)) {
            throw ApiException.badRequest(ErrorCodes.BRANCH_NOT_ACCESSIBLE,
                    "Admin must be assigned all active branches");
        }
        return List.copyOf(branchIds);
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
        auditService.write(adminUserId, null, AuditActions.USER_LOCKED, "User", userId.longValue(),
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
        auditService.write(adminUserId, null, AuditActions.USER_UNLOCKED, "User", userId.longValue(),
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
        auditService.write(adminUserId, null, AuditActions.USER_DELETED, "User", userId.longValue(),
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
        auditService.write(adminUserId, null, AuditActions.USER_ACTIVATED, "User", userId.longValue(),
            String.format("User %s activated by admin, IP: %s", user.getUsername(), ipAddress));
    }

    /**
     * Reset mật khẩu user (admin)
     */
    public void resetPassword(Integer userId, String newPassword, Integer adminUserId, String ipAddress) {
        // Kiểm tra chính sách mật khẩu
        validatePassword(newPassword);
        
        User user = userRepository.getById(userId)
            .orElseThrow(() -> ApiException.notFound(ErrorCodes.USER_NOT_FOUND, "User not found"));
        
        // Cập nhật hash mật khẩu
        String newHash = passwordHasher.hash(newPassword);
        userRepository.updatePasswordHash(userId, newHash);
        
        // Revoke refresh tokens
        refreshTokenRepository.revokeAllByUserId(userId);
        
        // Clear cache
        userStatusCache.evict(userId);
        
        // Audit (không ghi mật khẩu)
        auditService.write(adminUserId, null, AuditActions.PASSWORD_RESET, "User", userId.longValue(),
            String.format("Password reset for user %s by admin, IP: %s", user.getUsername(), ipAddress));
    }

    /**
     * Kiểm tra chính sách mật khẩu
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
     * Tạo UserResponse từ thực thể User
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

}

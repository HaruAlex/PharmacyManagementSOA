package services;

import com.typesafe.config.Config;
import domain.*;
import dto.LoginResponse;
import errors.ApiException;
import repositories.*;
import security.PasswordHasher;
import security.TokenService;
import security.UserStatusCache;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service xử lý authentication logic
 */
@Singleton
public class AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserBranchRepository userBranchRepository;
    private final AuditLogRepository auditLogRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private final UserStatusCache userStatusCache;
    private final Clock clock;
    private final Config config;
    
    private final int maxFailedAttempts;
    private final int accessTokenMinutes;

    @Inject
    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserBranchRepository userBranchRepository,
            AuditLogRepository auditLogRepository,
            RefreshTokenService refreshTokenService,
            PasswordHasher passwordHasher,
            TokenService tokenService,
            UserStatusCache userStatusCache,
            Clock clock,
            Config config) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userBranchRepository = userBranchRepository;
        this.auditLogRepository = auditLogRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.userStatusCache = userStatusCache;
        this.clock = clock;
        this.config = config;
        
        this.maxFailedAttempts = config.getInt("auth.maxFailedAttempts");
        this.accessTokenMinutes = config.getInt("jwt.accessTokenMinutes");
    }

    /**
     * Đăng nhập bằng username hoặc email
     * @param usernameOrEmail username hoặc email
     * @param password mật khẩu
     * @param ipAddress địa chỉ IP (để ghi audit)
     * @param userAgent User-Agent (để ghi audit)
     * @return LoginResponse nếu thành công
     * @throws ApiException nếu thất bại
     */
    public LoginResponse login(String usernameOrEmail, String password, String ipAddress, String userAgent) {
        // 1. Tìm user
        Optional<User> userOpt = userRepository.getByUsernameOrEmail(usernameOrEmail);
        
        if (userOpt.isEmpty()) {
            // Chống timing attack: thực hiện BCrypt verify giả
            passwordHasher.verify("dummy-password-to-prevent-timing-attack", 
                "$2a$11$dummyhashxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
            
            writeAuditLog(null, null, AuditActions.LOGIN_FAILED, "User", null,
                String.format("Failed login attempt for: %s, IP: %s", usernameOrEmail, ipAddress));
            
            throw ApiException.unauthorized(ErrorCodes.AUTH_INVALID_CREDENTIALS, 
                "Invalid username, email, or password");
        }
        
        User user = userOpt.get();
        
        // 2. Kiểm tra IsActive
        if (!user.isActive()) {
            writeAuditLog(user.getUserId(), null, AuditActions.LOGIN_FAILED, "User", user.getUserId(),
                String.format("Inactive account login attempt, IP: %s", ipAddress));
            
            throw ApiException.unauthorized(ErrorCodes.AUTH_ACCOUNT_INACTIVE, 
                "Invalid username, email, or password"); // Dùng chung message để không lộ thông tin
        }
        
        // 3. Kiểm tra IsLocked
        if (user.isLocked()) {
            writeAuditLog(user.getUserId(), null, AuditActions.LOGIN_FAILED, "User", user.getUserId(),
                String.format("Locked account login attempt, IP: %s", ipAddress));
            
            throw ApiException.locked(ErrorCodes.AUTH_ACCOUNT_LOCKED, 
                "Account is locked. Please contact administrator.");
        }
        
        // 4. Lấy role và kiểm tra IsActive
        Optional<Role> roleOpt = roleRepository.getById(user.getRoleId());
        if (roleOpt.isEmpty() || !roleOpt.get().isActive()) {
            writeAuditLog(user.getUserId(), null, AuditActions.LOGIN_FAILED, "User", user.getUserId(),
                "Role not found or inactive");
            
            throw ApiException.unauthorized(ErrorCodes.AUTH_ROLE_INACTIVE, 
                "Invalid username, email, or password");
        }
        
        Role role = roleOpt.get();
        
        // 5. Verify password
        boolean passwordValid = passwordHasher.verify(password, user.getPasswordHash());
        
        if (!passwordValid) {
            // Tăng FailedLoginCount
            userRepository.incrementFailedLogin(user.getUserId());
            int newFailedCount = user.getFailedLoginCount() + 1;
            
            // Nếu đạt ngưỡng thì khóa tài khoản
            if (newFailedCount >= maxFailedAttempts) {
                userRepository.setLocked(user.getUserId(), true);
                userStatusCache.evict(user.getUserId());
                
                writeAuditLog(user.getUserId(), null, AuditActions.ACCOUNT_AUTO_LOCKED, "User", user.getUserId(),
                    String.format("Account auto-locked after %d failed attempts, IP: %s", newFailedCount, ipAddress));
                
                throw ApiException.locked(ErrorCodes.AUTH_ACCOUNT_LOCKED, 
                    "Account is locked due to multiple failed login attempts. Please contact administrator.");
            }
            
            writeAuditLog(user.getUserId(), null, AuditActions.LOGIN_FAILED, "User", user.getUserId(),
                String.format("Wrong password (attempt %d/%d), IP: %s", newFailedCount, maxFailedAttempts, ipAddress));
            
            throw ApiException.unauthorized(ErrorCodes.AUTH_INVALID_CREDENTIALS, 
                "Invalid username, email, or password");
        }
        
        // 6. Đăng nhập thành công - reset FailedLoginCount
        userRepository.resetFailedLogin(user.getUserId());
        
        // 7. Lấy branchIds
        List<Integer> branchIds = userBranchRepository.getBranchIdsByUserId(user.getUserId());
        
        // 8. Tự động chọn branch nếu chỉ có 1
        Integer currentBranchId = null;
        if (branchIds.size() == 1) {
            currentBranchId = branchIds.get(0);
        }
        
        // 9. Lấy permissions
        List<Permission> permissions = permissionRepository.getByUserId(user.getUserId());
        List<String> permissionCodes = permissions.stream()
            .map(Permission::getPermissionCode)
            .collect(Collectors.toList());
        
        // 10. Tạo access token
        String accessToken = tokenService.createAccessToken(
            user.getUserId(), 
            role.getRoleCode(), 
            branchIds, 
            currentBranchId
        );
        
        // 11. Tạo refresh token (thật)
        String refreshToken = refreshTokenService.createRefreshToken(user.getUserId(), ipAddress);
        
        // 12. Ghi audit log
        writeAuditLog(user.getUserId(), currentBranchId, AuditActions.LOGIN_SUCCESS, "User", user.getUserId(),
            String.format("Successful login, IP: %s, User-Agent: %s", ipAddress, userAgent));
        
        // 13. Build response
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setUserId(user.getUserId());
        userInfo.setUsername(user.getUsername());
        userInfo.setFullName(user.getFullName());
        userInfo.setRoleCode(role.getRoleCode());
        userInfo.setRoleName(role.getRoleName());
        userInfo.setBranchIds(branchIds);
        userInfo.setCurrentBranchId(currentBranchId);
        userInfo.setPermissions(permissionCodes);
        
        return new LoginResponse(
            accessToken,
            accessTokenMinutes * 60, // convert to seconds
            refreshToken,
            userInfo
        );
    }
    
    /**
     * Verify token và kiểm tra trạng thái user
     */
    public TokenService.TokenPayload verifyAndCheckStatus(String token) {
        // 1. Verify JWT signature và expiry
        TokenService.TokenPayload payload = tokenService.verifyToken(token);
        
        // 2. Kiểm tra trạng thái user qua cache
        UserStatusCache.UserStatus cachedStatus = userStatusCache.get(payload.getUserId());
        
        if (cachedStatus == null) {
            // Cache miss - query DB
            Optional<User> userOpt = userRepository.getById(payload.getUserId());
            if (userOpt.isEmpty()) {
                throw ApiException.unauthorized(ErrorCodes.AUTH_TOKEN_REVOKED, "Token is no longer valid");
            }
            
            User user = userOpt.get();
            Optional<Role> roleOpt = roleRepository.getById(user.getRoleId());
            
            cachedStatus = new UserStatusCache.UserStatus(
                user.isActive(),
                user.isLocked(),
                user.getRoleId(),
                roleOpt.map(Role::getRoleCode).orElse(null)
            );
            
            userStatusCache.put(payload.getUserId(), cachedStatus);
        }
        
        // 3. Kiểm tra trạng thái
        if (!cachedStatus.isValid()) {
            throw ApiException.unauthorized(ErrorCodes.AUTH_TOKEN_REVOKED, "Token is no longer valid");
        }
        
        // 4. Kiểm tra role có đổi không
        if (!cachedStatus.getRoleCode().equals(payload.getRoleCode())) {
            throw ApiException.unauthorized(ErrorCodes.AUTH_TOKEN_REVOKED, "User role has changed");
        }
        
        return payload;
    }
    
    /**
     * Ghi audit log (không throw exception nếu lỗi)
     */
    private void writeAuditLog(Integer userId, Integer branchId, String actionCode, 
                              String entityName, Integer entityId, String details) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setBranchId(branchId);
            log.setActionCode(actionCode);
            log.setEntityName(entityName);
            log.setEntityId(entityId != null ? entityId.longValue() : null);
            log.setDetails(details);
            log.setCreatedAt(clock.instant());
            
            auditLogRepository.write(log);
        } catch (Exception e) {
            // Log error nhưng không fail request chính
            System.err.println("Failed to write audit log: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

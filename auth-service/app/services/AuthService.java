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
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private final UserStatusCache userStatusCache;
    private final Config config;
    
    private final int maxFailedAttempts;
    private final int accessTokenMinutes;

    @Inject
    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserBranchRepository userBranchRepository,
            RefreshTokenService refreshTokenService,
            AuditService auditService,
            PasswordHasher passwordHasher,
            TokenService tokenService,
            UserStatusCache userStatusCache,
            Config config) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userBranchRepository = userBranchRepository;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.userStatusCache = userStatusCache;
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
            
            auditService.write(null, null, AuditActions.LOGIN_FAILED, "User", null,
                String.format("Failed login attempt for: %s, IP: %s", usernameOrEmail, ipAddress));
            
            throw ApiException.unauthorized(ErrorCodes.AUTH_INVALID_CREDENTIALS, 
                "Invalid username, email, or password");
        }
        
        User user = userOpt.get();
        
        // 2. Kiểm tra IsActive
        if (!user.isActive()) {
            auditService.write(user.getUserId(), null, AuditActions.LOGIN_FAILED, "User", user.getUserId().longValue(),
                String.format("Inactive account login attempt, IP: %s", ipAddress));
            
            throw ApiException.unauthorized(ErrorCodes.AUTH_ACCOUNT_INACTIVE, 
                "Invalid username, email, or password"); // Dùng chung message để không lộ thông tin
        }
        
        // 3. Kiểm tra IsLocked
        if (user.isLocked()) {
            auditService.write(user.getUserId(), null, AuditActions.LOGIN_FAILED, "User", user.getUserId().longValue(),
                String.format("Locked account login attempt, IP: %s", ipAddress));
            
            throw ApiException.locked(ErrorCodes.AUTH_ACCOUNT_LOCKED, 
                "Account is locked. Please contact administrator.");
        }
        
        // 4. Lấy role và kiểm tra IsActive
        Optional<Role> roleOpt = roleRepository.getById(user.getRoleId());
        if (roleOpt.isEmpty() || !roleOpt.get().isActive()) {
            auditService.write(user.getUserId(), null, AuditActions.LOGIN_FAILED, "User", user.getUserId().longValue(),
                "Role not found or inactive");
            
            throw ApiException.unauthorized(ErrorCodes.AUTH_ROLE_INACTIVE, 
                "Invalid username, email, or password");
        }
        
        Role role = roleOpt.get();
        
        // 5. Xác thực mật khẩu
        boolean passwordValid = passwordHasher.verify(password, user.getPasswordHash());
        
        if (!passwordValid) {
            // Tăng FailedLoginCount
            userRepository.incrementFailedLogin(user.getUserId());
            int newFailedCount = user.getFailedLoginCount() + 1;
            
            // Nếu đạt ngưỡng thì khóa tài khoản
            if (newFailedCount >= maxFailedAttempts) {
                userRepository.setLocked(user.getUserId(), true);
                userStatusCache.evict(user.getUserId());
                
                auditService.write(user.getUserId(), null, AuditActions.ACCOUNT_AUTO_LOCKED, "User", user.getUserId().longValue(),
                    String.format("Account auto-locked after %d failed attempts, IP: %s", newFailedCount, ipAddress));
                
                throw ApiException.locked(ErrorCodes.AUTH_ACCOUNT_LOCKED, 
                    "Account is locked due to multiple failed login attempts. Please contact administrator.");
            }
            
            auditService.write(user.getUserId(), null, AuditActions.LOGIN_FAILED, "User", user.getUserId().longValue(),
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
        auditService.write(user.getUserId(), currentBranchId, AuditActions.LOGIN_SUCCESS, "User", user.getUserId().longValue(),
            String.format("Successful login, IP: %s, User-Agent: %s", ipAddress, userAgent));
        
        // 13. Tạo response
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
     * Xác thực token và kiểm tra trạng thái người dùng
     */
    public TokenService.TokenPayload verifyAndCheckStatus(String token) {
        // 1. Xác thực chữ ký JWT và thời hạn
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
    
}

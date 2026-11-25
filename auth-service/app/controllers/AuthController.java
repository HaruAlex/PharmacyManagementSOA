package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import dto.LoginRequest;
import dto.LoginResponse;
import dto.RefreshTokenRequest;
import dto.RefreshTokenResponse;
import dto.SelectBranchRequest;
import dto.TokenValidateResponse;
import dto.UserMeResponse;
import errors.ApiException;
import play.data.Form;
import play.data.FormFactory;
import play.libs.Json;
import play.libs.concurrent.ClassLoaderExecutionContext;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import repositories.*;
import security.TokenService;
import security.UserStatusCache;
import services.AuthService;
import services.RefreshTokenService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Controller xử lý authentication endpoints
 */
@Singleton
public class AuthController extends Controller {
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserBranchRepository userBranchRepository;
    private final AuditLogRepository auditLogRepository;
    private final TokenService tokenService;
    private final UserStatusCache userStatusCache;
    private final ClassLoaderExecutionContext dbContext;
    private final int accessTokenMinutes;

    @Inject
    public AuthController(
            AuthService authService,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserBranchRepository userBranchRepository,
            AuditLogRepository auditLogRepository,
            TokenService tokenService,
            UserStatusCache userStatusCache,
            ClassLoaderExecutionContext dbContext,
            Config config) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userBranchRepository = userBranchRepository;
        this.auditLogRepository = auditLogRepository;
        this.tokenService = tokenService;
        this.userStatusCache = userStatusCache;
        this.dbContext = dbContext;
        this.accessTokenMinutes = config.getInt("jwt.accessTokenMinutes");
    }

    /**
     * POST /api/auth/login
     * Đăng nhập bằng username hoặc email
     */
    public CompletionStage<Result> login(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            // Parse request body
            JsonNode json = request.body().asJson();
            if (json == null) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
            }
            
            LoginRequest loginReq = Json.fromJson(json, LoginRequest.class);
            
            if (loginReq.getUsernameOrEmail() == null || loginReq.getUsernameOrEmail().isBlank()) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Username or email is required");
            }
            if (loginReq.getPassword() == null || loginReq.getPassword().isBlank()) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Password is required");
            }
            
            // Lấy IP và User-Agent
            String ipAddress = request.remoteAddress();
            String userAgent = request.header("User-Agent").orElse("Unknown");
            
            // Thực hiện login
            LoginResponse response = authService.login(
                loginReq.getUsernameOrEmail(),
                loginReq.getPassword(),
                ipAddress,
                userAgent
            );
            
            return ok(Json.toJson(response));
            
        }, dbContext.current());
    }

    /**
     * GET /api/auth/validate
     * Token introspection cho Gateway/services khác
     */
    public CompletionStage<Result> validate(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            String token = extractToken(request);
            if (token == null) {
                return ok(Json.toJson(new TokenValidateResponse(false)));
            }
            
            try {
                TokenService.TokenPayload payload = authService.verifyAndCheckStatus(token);
                
                TokenValidateResponse response = new TokenValidateResponse(
                    true,
                    payload.getUserId(),
                    payload.getRoleCode(),
                    payload.getBranchIds(),
                    payload.getCurrentBranchId()
                );
                
                return ok(Json.toJson(response));
                
            } catch (Exception e) {
                return ok(Json.toJson(new TokenValidateResponse(false)));
            }
            
        }, dbContext.current());
    }

    /**
     * GET /api/auth/me
     * Lấy thông tin user hiện tại
     */
    public CompletionStage<Result> me(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            String token = extractToken(request);
            if (token == null) {
                throw ApiException.unauthorized("AUTH_TOKEN_INVALID", "Missing or invalid authorization header");
            }
            
            // Verify token và trạng thái
            TokenService.TokenPayload payload = authService.verifyAndCheckStatus(token);
            
            // Lấy thông tin user
            var user = userRepository.getById(payload.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("AUTH_TOKEN_REVOKED", "User not found"));
            
            var role = roleRepository.getById(user.getRoleId())
                .orElseThrow(() -> ApiException.unauthorized("AUTH_TOKEN_REVOKED", "Role not found"));
            
            var branchIds = userBranchRepository.getBranchIdsByUserId(user.getUserId());
            
            var permissions = permissionRepository.getByUserId(user.getUserId());
            var permissionCodes = permissions.stream()
                .map(p -> p.getPermissionCode())
                .collect(Collectors.toList());
            
            UserMeResponse response = new UserMeResponse();
            response.setUserId(user.getUserId());
            response.setUsername(user.getUsername());
            response.setFullName(user.getFullName());
            response.setEmail(user.getEmail());
            response.setPhone(user.getPhone());
            response.setRoleCode(role.getRoleCode());
            response.setRoleName(role.getRoleName());
            response.setBranchIds(branchIds);
            response.setCurrentBranchId(payload.getCurrentBranchId());
            response.setPermissions(permissionCodes);
            
            return ok(Json.toJson(response));
            
        }, dbContext.current());
    }

    /**
     * Extract Bearer token từ Authorization header
     */
    private String extractToken(Http.Request request) {
        return request.header("Authorization")
            .filter(h -> h.startsWith("Bearer "))
            .map(h -> h.substring(7))
            .orElse(null);
    }

    /**
     * POST /api/auth/refresh
     * Refresh access token (rotation)
     * Không yêu cầu access token - chỉ cần refresh token
     */
    public CompletionStage<Result> refresh(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode json = request.body().asJson();
            if (json == null) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
            }
            
            dto.RefreshTokenRequest refreshReq = Json.fromJson(json, dto.RefreshTokenRequest.class);
            if (refreshReq.getRefreshToken() == null || refreshReq.getRefreshToken().isBlank()) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Refresh token is required");
            }
            
            String ipAddress = request.remoteAddress();
            
            // Lấy userId từ refresh token hash
            String tokenHash = security.RefreshTokenGenerator.hashToken(refreshReq.getRefreshToken());
            Optional<domain.RefreshToken> tokenOpt = refreshTokenService.getByTokenHash(tokenHash);
            if (tokenOpt.isEmpty()) {
                throw ApiException.unauthorized(domain.ErrorCodes.AUTH_REFRESH_INVALID, "Invalid refresh token");
            }
            
            Integer userId = tokenOpt.get().getUserId();
            
            // Rotate refresh token (sẽ validate token trong method này)
            String newRefreshToken = refreshTokenService.rotateRefreshToken(
                userId,
                refreshReq.getRefreshToken(),
                ipAddress
            );
            
            // Lấy thông tin user mới nhất (role, branches có thể đã thay đổi)
            var user = userRepository.getById(userId)
                .orElseThrow(() -> ApiException.unauthorized("AUTH_TOKEN_REVOKED", "User not found"));
            
            var role = roleRepository.getById(user.getRoleId())
                .orElseThrow(() -> ApiException.unauthorized("AUTH_TOKEN_REVOKED", "Role not found"));
            
            var branchIds = userBranchRepository.getBranchIdsByUserId(user.getUserId());
            
            // Không có current_branch_id (user phải chọn lại)
            Integer currentBranchId = null;
            
            // Tạo access token mới
            String newAccessToken = tokenService.createAccessToken(
                user.getUserId(),
                role.getRoleCode(),
                branchIds,
                currentBranchId
            );
            
            dto.RefreshTokenResponse response = new dto.RefreshTokenResponse(
                newAccessToken,
                accessTokenMinutes * 60,
                newRefreshToken
            );
            
            return ok(Json.toJson(response));
            
        }, dbContext.current());
    }

    /**
     * POST /api/auth/logout
     * Logout - revoke refresh token
     */
    public CompletionStage<Result> logout(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode json = request.body().asJson();
            if (json == null) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
            }
            
            dto.RefreshTokenRequest logoutReq = Json.fromJson(json, dto.RefreshTokenRequest.class);
            if (logoutReq.getRefreshToken() == null || logoutReq.getRefreshToken().isBlank()) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Refresh token is required");
            }
            
            String token = extractToken(request);
            if (token == null) {
                throw ApiException.unauthorized("AUTH_TOKEN_INVALID", "Missing or invalid authorization header");
            }
            
            TokenService.TokenPayload payload = authService.verifyAndCheckStatus(token);
            String ipAddress = request.remoteAddress();
            
            refreshTokenService.revokeToken(logoutReq.getRefreshToken(), payload.getUserId(), ipAddress);
            
            return ok(Json.toJson(java.util.Map.of("message", "Logged out successfully")));
            
        }, dbContext.current());
    }

    /**
     * POST /api/auth/logout-all
     * Logout from all devices
     */
    public CompletionStage<Result> logoutAll(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            String token = extractToken(request);
            if (token == null) {
                throw ApiException.unauthorized("AUTH_TOKEN_INVALID", "Missing or invalid authorization header");
            }
            
            TokenService.TokenPayload payload = authService.verifyAndCheckStatus(token);
            String ipAddress = request.remoteAddress();
            
            int revokedCount = refreshTokenService.revokeAllTokens(payload.getUserId(), ipAddress);
            
            return ok(Json.toJson(java.util.Map.of(
                "message", "Logged out from all devices",
                "revokedCount", revokedCount
            )));
            
        }, dbContext.current());
    }

    /**
     * PUT /api/auth/current-branch
     * Chọn chi nhánh hiện hành
     */
    public CompletionStage<Result> selectBranch(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            String token = extractToken(request);
            if (token == null) {
                throw ApiException.unauthorized("AUTH_TOKEN_INVALID", "Missing or invalid authorization header");
            }
            
            TokenService.TokenPayload payload = authService.verifyAndCheckStatus(token);
            
            JsonNode json = request.body().asJson();
            if (json == null) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
            }
            
            dto.SelectBranchRequest branchReq = Json.fromJson(json, dto.SelectBranchRequest.class);
            if (branchReq.getBranchId() == null) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Branch ID is required");
            }
            
            // Kiểm tra branch có trong danh sách của user không
            var branchIds = userBranchRepository.getBranchIdsByUserId(payload.getUserId());
            if (!branchIds.contains(branchReq.getBranchId())) {
                throw ApiException.forbidden(domain.ErrorCodes.BRANCH_NOT_ACCESSIBLE, 
                    "You do not have access to this branch");
            }
            
            // TODO Phase 3: Kiểm tra branch còn active qua BranchLookupClient
            // boolean isActive = branchLookupClient.isBranchActive(branchReq.getBranchId()).toCompletableFuture().join();
            // if (!isActive) throw ApiException.badRequest(ErrorCodes.BRANCH_INACTIVE, "Branch is inactive");
            
            // Lấy thông tin user
            var user = userRepository.getById(payload.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("AUTH_TOKEN_REVOKED", "User not found"));
            
            var role = roleRepository.getById(user.getRoleId())
                .orElseThrow(() -> ApiException.unauthorized("AUTH_TOKEN_REVOKED", "Role not found"));
            
            // Phát hành access token mới với current_branch_id
            String newAccessToken = tokenService.createAccessToken(
                user.getUserId(),
                role.getRoleCode(),
                branchIds,
                branchReq.getBranchId()
            );
            
            // Ghi audit log
            try {
                domain.AuditLog auditLog = new domain.AuditLog();
                auditLog.setUserId(user.getUserId());
                auditLog.setBranchId(branchReq.getBranchId());
                auditLog.setActionCode(domain.AuditActions.BRANCH_SELECTED);
                auditLog.setEntityName("Branch");
                auditLog.setEntityId(branchReq.getBranchId().longValue());
                auditLog.setDetails(String.format("Branch %d selected, IP: %s", branchReq.getBranchId(), request.remoteAddress()));
                auditLogRepository.write(auditLog);
            } catch (Exception e) {
                // Ignore audit error
            }
            
            dto.RefreshTokenResponse response = new dto.RefreshTokenResponse(
                newAccessToken,
                accessTokenMinutes * 60,
                null // Không cần refresh token mới
            );
            
            return ok(Json.toJson(response));
            
        }, dbContext.current());
    }
}

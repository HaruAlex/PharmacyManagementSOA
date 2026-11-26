package controllers;

import actions.RateLimited;
import clients.BranchLookupClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import services.AuditService;
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
    private final AuditService auditService;
    private final TokenService tokenService;
    private final UserStatusCache userStatusCache;
    private final ClassLoaderExecutionContext dbContext;
    private final int accessTokenMinutes;
    private final BranchLookupClient branchLookupClient;

    @Inject
    public AuthController(
            AuthService authService,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserBranchRepository userBranchRepository,
            AuditService auditService,
            TokenService tokenService,
            UserStatusCache userStatusCache,
            ClassLoaderExecutionContext dbContext,
            Config config,
            BranchLookupClient branchLookupClient) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userBranchRepository = userBranchRepository;
        this.auditService = auditService;
        this.tokenService = tokenService;
        this.userStatusCache = userStatusCache;
        this.dbContext = dbContext;
        this.accessTokenMinutes = config.getInt("jwt.accessTokenMinutes");
        this.branchLookupClient = branchLookupClient;
    }

    /**
     * POST /api/auth/login
     * Đăng nhập bằng username hoặc email
     */
    @RateLimited(RateLimited.Policy.LOGIN)
    public CompletionStage<Result> login(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            // Đọc dữ liệu từ phần thân yêu cầu
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
            
            // Xác thực token và trạng thái
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
     * Trích xuất Bearer token từ header Authorization
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
    @RateLimited(RateLimited.Policy.REFRESH)
    public CompletionStage<Result> refresh(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
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
                
                // Xoay vòng refresh token; phương thức này đồng thời xác thực token
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
            } catch (ApiException e) {
                ObjectNode error = play.libs.Json.newObject();
                error.put("status", e.getStatus());
                error.put("code", e.getCode());
                error.put("message", e.getMessage());
                return status(e.getStatus(), error);
            }
        }, dbContext.current());
    }

    /**
     * POST /api/auth/logout
     * Đăng xuất và thu hồi refresh token
     */
    public CompletionStage<Result> logout(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
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
            } catch (ApiException e) {
                ObjectNode error = play.libs.Json.newObject();
                error.put("status", e.getStatus());
                error.put("code", e.getCode());
                error.put("message", e.getMessage());
                return status(e.getStatus(), error);
            }
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
                throw ApiException.unauthorized(
                        "AUTH_TOKEN_INVALID", "Missing or invalid authorization header");
            }

            TokenService.TokenPayload payload = authService.verifyAndCheckStatus(token);
            JsonNode json = request.body().asJson();
            if (json == null) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
            }

            SelectBranchRequest branchRequest = Json.fromJson(json, SelectBranchRequest.class);
            if (branchRequest == null || branchRequest.getBranchId() == null) {
                throw ApiException.badRequest("VALIDATION_FAILED", "Branch ID is required");
            }

            var branchIds = userBranchRepository.getBranchIdsByUserId(payload.getUserId());
            if (!branchIds.contains(branchRequest.getBranchId())) {
                throw ApiException.forbidden(
                        domain.ErrorCodes.BRANCH_NOT_ACCESSIBLE,
                        "You do not have access to this branch");
            }

            var user = userRepository.getById(payload.getUserId())
                    .orElseThrow(() -> ApiException.unauthorized(
                            "AUTH_TOKEN_REVOKED", "User not found"));
            var role = roleRepository.getById(user.getRoleId())
                    .orElseThrow(() -> ApiException.unauthorized(
                            "AUTH_TOKEN_REVOKED", "Role not found"));

            return new BranchSelection(user, role, branchIds, branchRequest.getBranchId());
        }, dbContext.current()).thenCompose(selection ->
                branchLookupClient.isBranchActive(selection.branchId)
                        .thenApplyAsync(isActive -> {
                            if (!Boolean.TRUE.equals(isActive)) {
                                throw ApiException.badRequest(
                                        domain.ErrorCodes.BRANCH_INACTIVE,
                                        "Branch is inactive or does not exist");
                            }

                            String newAccessToken = tokenService.createAccessToken(
                                    selection.user.getUserId(),
                                    selection.role.getRoleCode(),
                                    selection.branchIds,
                                    selection.branchId);
                            auditService.write(
                                    selection.user.getUserId(),
                                    selection.branchId,
                                    domain.AuditActions.BRANCH_SELECTED,
                                    "Branch",
                                    selection.branchId.longValue(),
                                    String.format(
                                            "Branch %d selected, IP: %s",
                                            selection.branchId,
                                            request.remoteAddress()));

                            dto.RefreshTokenResponse response = new dto.RefreshTokenResponse(
                                    newAccessToken,
                                    accessTokenMinutes * 60,
                                    null);
                            return ok(Json.toJson(response));
                        }, dbContext.current()))
                .exceptionally(error -> {
                    Throwable cause = error;
                    while (cause instanceof java.util.concurrent.CompletionException
                            && cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    if (cause instanceof ApiException) {
                        ApiException apiException = (ApiException) cause;
                        ObjectNode errorResponse = Json.newObject();
                        errorResponse.put("status", apiException.getStatus());
                        errorResponse.put("code", apiException.getCode());
                        errorResponse.put("message", apiException.getMessage());
                        return status(apiException.getStatus(), errorResponse);
                    }
                    throw new java.util.concurrent.CompletionException(cause);
                });
    }

    private static final class BranchSelection {
        private final domain.User user;
        private final domain.Role role;
        private final java.util.List<Integer> branchIds;
        private final Integer branchId;

        private BranchSelection(
                domain.User user,
                domain.Role role,
                java.util.List<Integer> branchIds,
                Integer branchId) {
            this.user = user;
            this.role = role;
            this.branchIds = branchIds;
            this.branchId = branchId;
        }
    }
}

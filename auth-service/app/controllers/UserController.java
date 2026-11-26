package controllers;

import actions.HasPermission;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dto.*;
import errors.ApiException;
import play.libs.Json;
import play.libs.concurrent.ClassLoaderExecutionContext;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import security.TokenService;
import services.AuthService;
import services.UserService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Controller quản lý user (chỉ dành cho Admin - role AD)
 */
@Singleton
@HasPermission("USER_MANAGE")
public class UserController extends Controller {
    private final UserService userService;
    private final AuthService authService;
    private final ClassLoaderExecutionContext dbContext;

    @Inject
    public UserController(
            UserService userService,
            AuthService authService,
            ClassLoaderExecutionContext dbContext) {
        this.userService = userService;
        this.authService = authService;
        this.dbContext = dbContext;
    }

    /**
     * POST /api/users - Tạo user mới
     */
    public CompletionStage<Result> create(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Xác thực admin
                TokenService.TokenPayload payload = requireAdmin(request);
                
                JsonNode json = request.body().asJson();
                if (json == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
                }
                
                CreateUserRequest createReq = Json.fromJson(json, CreateUserRequest.class);
                
                // Kiểm tra các trường dữ liệu
                if (createReq.getFullName() == null || createReq.getFullName().isBlank()) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Full name is required");
                }
                if (createReq.getUsername() == null || createReq.getUsername().isBlank()) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Username is required");
                }
                if (createReq.getPassword() == null || createReq.getPassword().isBlank()) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Password is required");
                }
                if (createReq.getEmail() == null || createReq.getEmail().isBlank()) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Email is required");
                }
                if (createReq.getRoleId() == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Role ID is required");
                }
                if (createReq.getBranchIds() == null || createReq.getBranchIds().isEmpty()) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "At least one branch is required");
                }
                
                String ipAddress = request.remoteAddress();
                UserResponse response = userService.createUser(createReq, payload.getUserId(), ipAddress);
                
                return created(Json.toJson(response));
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
     * GET /api/users - Danh sách user (phân trang, lọc)
     */
    public CompletionStage<Result> list(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Xác thực admin
                requireAdmin(request);
                
                // Đọc các tham số truy vấn
                String name = request.getQueryString("name");
                String email = request.getQueryString("email");
                String roleIdStr = request.getQueryString("roleId");
                String status = request.getQueryString("status");
                String pageStr = request.getQueryString("page");
                String pageSizeStr = request.getQueryString("pageSize");
                String sortBy = request.getQueryString("sortBy");
                String sortOrder = request.getQueryString("sortOrder");
                
                Integer roleId = null;
                if (roleIdStr != null && !roleIdStr.isBlank()) {
                    try {
                        roleId = Integer.parseInt(roleIdStr);
                    } catch (NumberFormatException e) {
                        throw ApiException.badRequest("VALIDATION_FAILED", "Invalid roleId");
                    }
                }
                
                int page = 1;
                if (pageStr != null && !pageStr.isBlank()) {
                    try {
                        page = Integer.parseInt(pageStr);
                    } catch (NumberFormatException e) {
                        throw ApiException.badRequest("VALIDATION_FAILED", "Invalid page");
                    }
                }
                
                int pageSize = 10;
                if (pageSizeStr != null && !pageSizeStr.isBlank()) {
                    try {
                        pageSize = Integer.parseInt(pageSizeStr);
                    } catch (NumberFormatException e) {
                        throw ApiException.badRequest("VALIDATION_FAILED", "Invalid pageSize");
                    }
                }
                
                if (sortBy == null || sortBy.isBlank()) {
                    sortBy = "createdAt";
                }
                if (sortOrder == null || sortOrder.isBlank()) {
                    sortOrder = "DESC";
                }
                
                PagedResult<UserResponse> result = userService.listUsers(
                    name, email, roleId, status, page, pageSize, sortBy, sortOrder
                );
                
                return ok(Json.toJson(result));
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
     * GET /api/users/:id - Chi tiết user
     */
    public CompletionStage<Result> get(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Xác thực admin
                requireAdmin(request);
                
                UserResponse response = userService.getUser(id);
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
     * PUT /api/users/:id - Cập nhật user (không đổi username)
     */
    public CompletionStage<Result> update(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Xác thực admin
                TokenService.TokenPayload payload = requireAdmin(request);
                
                JsonNode json = request.body().asJson();
                if (json == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
                }
                
                UpdateUserRequest updateReq = Json.fromJson(json, UpdateUserRequest.class);
                
                // Kiểm tra dữ liệu đầu vào
                if (updateReq.getFullName() == null || updateReq.getFullName().isBlank()) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Full name is required");
                }
                if (updateReq.getEmail() == null || updateReq.getEmail().isBlank()) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Email is required");
                }
                if (updateReq.getRoleId() == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Role ID is required");
                }
                
                String ipAddress = request.remoteAddress();
                UserResponse response = userService.updateUser(id, updateReq, payload.getUserId(), ipAddress);
                
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
     * PUT /api/users/:id/role-branches - Gán role + branches
     */
    public CompletionStage<Result> updateRoleBranches(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Xác thực admin
                TokenService.TokenPayload payload = requireAdmin(request);
                
                JsonNode json = request.body().asJson();
                if (json == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
                }
                
                UpdateRoleBranchesRequest updateReq = Json.fromJson(json, UpdateRoleBranchesRequest.class);
                
                // Kiểm tra dữ liệu đầu vào
                if (updateReq.getRoleId() == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Role ID is required");
                }
                if (updateReq.getBranchIds() == null || updateReq.getBranchIds().isEmpty()) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "At least one branch is required");
                }
                
                String ipAddress = request.remoteAddress();
                UserResponse response = userService.updateRoleAndBranches(id, updateReq, payload.getUserId(), ipAddress);
                
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
     * POST /api/users/:id/lock - Khóa user
     */
    public CompletionStage<Result> lock(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Xác thực admin
                TokenService.TokenPayload payload = requireAdmin(request);
                
                String ipAddress = request.remoteAddress();
                userService.lockUser(id, payload.getUserId(), ipAddress);
                
                return ok(Json.toJson(java.util.Map.of("message", "User locked successfully")));
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
     * POST /api/users/:id/unlock - Mở khóa user
     */
    public CompletionStage<Result> unlock(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Xác thực admin
                TokenService.TokenPayload payload = requireAdmin(request);
                
                String ipAddress = request.remoteAddress();
                userService.unlockUser(id, payload.getUserId(), ipAddress);
                
                return ok(Json.toJson(java.util.Map.of("message", "User unlocked successfully")));
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
     * DELETE /api/users/:id - Xóa mềm user (IsActive=0)
     */
    public CompletionStage<Result> delete(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Xác thực admin
                TokenService.TokenPayload payload = requireAdmin(request);
                
                String ipAddress = request.remoteAddress();
                userService.deleteUser(id, payload.getUserId(), ipAddress);
                
                return ok(Json.toJson(java.util.Map.of("message", "User deleted successfully")));
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
     * POST /api/users/:id/activate - Kích hoạt lại user
     */
    public CompletionStage<Result> activate(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Xác thực admin
                TokenService.TokenPayload payload = requireAdmin(request);
                
                String ipAddress = request.remoteAddress();
                userService.activateUser(id, payload.getUserId(), ipAddress);
                
                return ok(Json.toJson(java.util.Map.of("message", "User activated successfully")));
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
     * POST /api/users/:id/reset-password - Admin reset mật khẩu
     */
    public CompletionStage<Result> resetPassword(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Xác thực admin
                TokenService.TokenPayload payload = requireAdmin(request);
                
                JsonNode json = request.body().asJson();
                if (json == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
                }
                
                ResetPasswordRequest resetReq = Json.fromJson(json, ResetPasswordRequest.class);
                
                if (resetReq.getNewPassword() == null || resetReq.getNewPassword().isBlank()) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "New password is required");
                }
                
                String ipAddress = request.remoteAddress();
                userService.resetPassword(id, resetReq.getNewPassword(), payload.getUserId(), ipAddress);
                
                return ok(Json.toJson(java.util.Map.of("message", "Password reset successfully")));
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
     * Trích xuất token và xác thực vai trò AD (admin)
     */
    private TokenService.TokenPayload requireAdmin(Http.Request request) {
        String token = request.header("Authorization")
            .filter(h -> h.startsWith("Bearer "))
            .map(h -> h.substring(7))
            .orElseThrow(() -> ApiException.unauthorized("AUTH_TOKEN_INVALID", "Missing or invalid authorization header"));
        
        TokenService.TokenPayload payload = authService.verifyAndCheckStatus(token);
        
        // Kiểm tra role AD
        if (!"AD".equals(payload.getRoleCode())) {
            throw ApiException.forbidden("FORBIDDEN", "Admin role required");
        }
        
        return payload;
    }
}

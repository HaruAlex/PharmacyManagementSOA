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
import services.RbacService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Controller quản lý Roles (chỉ Admin)
 */
@Singleton
@HasPermission("USER_MANAGE")
public class RoleController extends Controller {
    private final RbacService rbacService;
    private final AuthService authService;
    private final ClassLoaderExecutionContext dbContext;

    @Inject
    public RoleController(
            RbacService rbacService,
            AuthService authService,
            ClassLoaderExecutionContext dbContext) {
        this.rbacService = rbacService;
        this.authService = authService;
        this.dbContext = dbContext;
    }

    /**
     * POST /api/roles - Tạo role mới
     */
    public CompletionStage<Result> create(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TokenService.TokenPayload payload = requireAdmin(request);
                
                JsonNode json = request.body().asJson();
                if (json == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
                }
                
                CreateRoleRequest createReq = Json.fromJson(json, CreateRoleRequest.class);
                String ipAddress = request.remoteAddress();
                
                RoleResponse response = rbacService.createRole(createReq, payload.getUserId(), ipAddress);
                
                return created(Json.toJson(response));
            } catch (ApiException e) {
                return errorResponse(e);
            }
        }, dbContext.current());
    }

    /**
     * GET /api/roles - Danh sách roles
     */
    public CompletionStage<Result> list(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                requireAdmin(request);
                
                List<RoleResponse> roles = rbacService.listRoles();
                
                return ok(Json.toJson(roles));
            } catch (ApiException e) {
                return errorResponse(e);
            }
        }, dbContext.current());
    }

    /**
     * GET /api/roles/:id - Chi tiết role
     */
    public CompletionStage<Result> get(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                requireAdmin(request);
                
                RoleResponse response = rbacService.getRole(id);
                
                return ok(Json.toJson(response));
            } catch (ApiException e) {
                return errorResponse(e);
            }
        }, dbContext.current());
    }

    /**
     * PUT /api/roles/:id - Cập nhật role
     */
    public CompletionStage<Result> update(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TokenService.TokenPayload payload = requireAdmin(request);
                
                JsonNode json = request.body().asJson();
                if (json == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
                }
                
                UpdateRoleRequest updateReq = Json.fromJson(json, UpdateRoleRequest.class);
                String ipAddress = request.remoteAddress();
                
                RoleResponse response = rbacService.updateRole(id, updateReq, payload.getUserId(), ipAddress);
                
                return ok(Json.toJson(response));
            } catch (ApiException e) {
                return errorResponse(e);
            }
        }, dbContext.current());
    }

    /**
     * GET /api/roles/:id/permissions - Lấy permissions của role
     */
    public CompletionStage<Result> getPermissions(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                requireAdmin(request);
                
                List<PermissionResponse> permissions = rbacService.getRolePermissions(id);
                
                return ok(Json.toJson(permissions));
            } catch (ApiException e) {
                return errorResponse(e);
            }
        }, dbContext.current());
    }

    /**
     * PUT /api/roles/:id/permissions - Cập nhật quyền của vai trò
     */
    public CompletionStage<Result> updatePermissions(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TokenService.TokenPayload payload = requireAdmin(request);
                
                JsonNode json = request.body().asJson();
                if (json == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
                }
                
                UpdateRolePermissionsRequest updateReq = Json.fromJson(json, UpdateRolePermissionsRequest.class);
                String ipAddress = request.remoteAddress();
                
                List<PermissionResponse> permissions = rbacService.updateRolePermissions(
                    id, updateReq, payload.getUserId(), ipAddress
                );
                
                return ok(Json.toJson(permissions));
            } catch (ApiException e) {
                return errorResponse(e);
            }
        }, dbContext.current());
    }

    private TokenService.TokenPayload requireAdmin(Http.Request request) {
        String token = request.header("Authorization")
            .filter(h -> h.startsWith("Bearer "))
            .map(h -> h.substring(7))
            .orElseThrow(() -> ApiException.unauthorized("AUTH_TOKEN_INVALID", 
                "Missing or invalid authorization header"));
        
        TokenService.TokenPayload payload = authService.verifyAndCheckStatus(token);
        
        if (!"AD".equals(payload.getRoleCode())) {
            throw ApiException.forbidden("FORBIDDEN", "Admin role required");
        }
        
        return payload;
    }

    private Result errorResponse(ApiException e) {
        ObjectNode error = Json.newObject();
        error.put("status", e.getStatus());
        error.put("code", e.getCode());
        error.put("message", e.getMessage());
        return status(e.getStatus(), error);
    }
}

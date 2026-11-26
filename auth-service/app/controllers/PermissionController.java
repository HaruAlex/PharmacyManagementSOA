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
 * Controller quản lý Permissions (chỉ Admin)
 */
@Singleton
@HasPermission("USER_MANAGE")
public class PermissionController extends Controller {
    private final RbacService rbacService;
    private final AuthService authService;
    private final ClassLoaderExecutionContext dbContext;

    @Inject
    public PermissionController(
            RbacService rbacService,
            AuthService authService,
            ClassLoaderExecutionContext dbContext) {
        this.rbacService = rbacService;
        this.authService = authService;
        this.dbContext = dbContext;
    }

    /**
     * POST /api/permissions - Tạo permission mới
     */
    public CompletionStage<Result> create(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TokenService.TokenPayload payload = requireAdmin(request);
                
                JsonNode json = request.body().asJson();
                if (json == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
                }
                
                CreatePermissionRequest createReq = Json.fromJson(json, CreatePermissionRequest.class);
                String ipAddress = request.remoteAddress();
                
                PermissionResponse response = rbacService.createPermission(createReq, payload.getUserId(), ipAddress);
                
                return created(Json.toJson(response));
            } catch (ApiException e) {
                return errorResponse(e);
            }
        }, dbContext.current());
    }

    /**
     * GET /api/permissions - Danh sách permissions
     */
    public CompletionStage<Result> list(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                requireAdmin(request);
                
                List<PermissionResponse> permissions = rbacService.listPermissions();
                
                return ok(Json.toJson(permissions));
            } catch (ApiException e) {
                return errorResponse(e);
            }
        }, dbContext.current());
    }

    /**
     * GET /api/permissions/:id - Chi tiết permission
     */
    public CompletionStage<Result> get(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                requireAdmin(request);
                
                PermissionResponse response = rbacService.getPermission(id);
                
                return ok(Json.toJson(response));
            } catch (ApiException e) {
                return errorResponse(e);
            }
        }, dbContext.current());
    }

    /**
     * PUT /api/permissions/:id - Cập nhật permission
     */
    public CompletionStage<Result> update(Integer id, Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TokenService.TokenPayload payload = requireAdmin(request);
                
                JsonNode json = request.body().asJson();
                if (json == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
                }
                
                UpdatePermissionRequest updateReq = Json.fromJson(json, UpdatePermissionRequest.class);
                String ipAddress = request.remoteAddress();
                
                PermissionResponse response = rbacService.updatePermission(id, updateReq, payload.getUserId(), ipAddress);
                
                return ok(Json.toJson(response));
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

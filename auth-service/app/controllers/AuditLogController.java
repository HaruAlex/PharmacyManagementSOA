package controllers;

import actions.HasPermission;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dto.AuditLogResponse;
import dto.PagedResult;
import errors.ApiException;
import play.libs.Json;
import play.libs.concurrent.ClassLoaderExecutionContext;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import security.TokenService;
import services.AuditService;
import services.AuthService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Controller quản lý Audit Logs (chỉ Admin)
 */
@Singleton
@HasPermission("USER_MANAGE")
public class AuditLogController extends Controller {
    private final AuditService auditService;
    private final AuthService authService;
    private final ClassLoaderExecutionContext dbContext;

    @Inject
    public AuditLogController(
            AuditService auditService,
            AuthService authService,
            ClassLoaderExecutionContext dbContext) {
        this.auditService = auditService;
        this.authService = authService;
        this.dbContext = dbContext;
    }

    /**
     * GET /api/audit-logs - Tìm kiếm audit logs
     * Query params: userId, actionCode, entityName, fromDate, toDate, page, pageSize, sortOrder
     */
    public CompletionStage<Result> search(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                requireAdmin(request);
                
                // Đọc các tham số truy vấn
                String userIdStr = request.getQueryString("userId");
                String actionCode = request.getQueryString("actionCode");
                String entityName = request.getQueryString("entityName");
                String fromDateStr = request.getQueryString("fromDate");
                String toDateStr = request.getQueryString("toDate");
                String pageStr = request.getQueryString("page");
                String pageSizeStr = request.getQueryString("pageSize");
                String sortOrder = request.getQueryString("sortOrder");
                
                // Đọc userId
                Integer userId = null;
                if (userIdStr != null && !userIdStr.isBlank()) {
                    try {
                        userId = Integer.parseInt(userIdStr);
                    } catch (NumberFormatException e) {
                        throw ApiException.badRequest("VALIDATION_FAILED", "Invalid userId");
                    }
                }
                
                // Đọc khoảng thời gian
                Instant fromDate = null;
                if (fromDateStr != null && !fromDateStr.isBlank()) {
                    try {
                        fromDate = Instant.parse(fromDateStr);
                    } catch (Exception e) {
                        throw ApiException.badRequest("VALIDATION_FAILED", 
                            "Invalid fromDate format. Use ISO-8601 (e.g., 2024-01-01T00:00:00Z)");
                    }
                }
                
                Instant toDate = null;
                if (toDateStr != null && !toDateStr.isBlank()) {
                    try {
                        toDate = Instant.parse(toDateStr);
                    } catch (Exception e) {
                        throw ApiException.badRequest("VALIDATION_FAILED", 
                            "Invalid toDate format. Use ISO-8601 (e.g., 2024-12-31T23:59:59Z)");
                    }
                }
                
                // Đọc tham số phân trang
                int page = 1;
                if (pageStr != null && !pageStr.isBlank()) {
                    try {
                        page = Integer.parseInt(pageStr);
                    } catch (NumberFormatException e) {
                        throw ApiException.badRequest("VALIDATION_FAILED", "Invalid page");
                    }
                }
                
                int pageSize = 20;
                if (pageSizeStr != null && !pageSizeStr.isBlank()) {
                    try {
                        pageSize = Integer.parseInt(pageSizeStr);
                    } catch (NumberFormatException e) {
                        throw ApiException.badRequest("VALIDATION_FAILED", "Invalid pageSize");
                    }
                }
                
                if (sortOrder == null || sortOrder.isBlank()) {
                    sortOrder = "DESC";
                }
                
                // Search
                PagedResult<AuditLogResponse> result = auditService.searchAuditLogs(
                    userId, actionCode, entityName, fromDate, toDate,
                    page, pageSize, sortOrder
                );
                
                return ok(Json.toJson(result));
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

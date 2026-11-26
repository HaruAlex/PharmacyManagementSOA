package controllers;

import actions.RateLimited;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dto.ChangePasswordRequest;
import dto.ProfileResponse;
import dto.UpdateProfileRequest;
import errors.ApiException;
import play.libs.Json;
import play.libs.concurrent.ClassLoaderExecutionContext;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import security.TokenService;
import services.AuthService;
import services.ProfileService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Controller xử lý profile và đổi mật khẩu (BE-48, BE-49)
 */
@Singleton
public class ProfileController extends Controller {
    private final ProfileService profileService;
    private final AuthService authService;
    private final ClassLoaderExecutionContext dbContext;

    @Inject
    public ProfileController(
            ProfileService profileService,
            AuthService authService,
            ClassLoaderExecutionContext dbContext) {
        this.profileService = profileService;
        this.authService = authService;
        this.dbContext = dbContext;
    }

    /**
     * GET /api/auth/profile - Lấy thông tin profile của user hiện tại
     */
    public CompletionStage<Result> getProfile(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TokenService.TokenPayload payload = requireAuth(request);
                
                ProfileResponse profile = profileService.getProfile(payload.getUserId());
                
                return ok(Json.toJson(profile));
            } catch (ApiException e) {
                ObjectNode error = Json.newObject();
                error.put("status", e.getStatus());
                error.put("code", e.getCode());
                error.put("message", e.getMessage());
                return status(e.getStatus(), error);
            }
        }, dbContext.current());
    }

    /**
     * PUT /api/auth/profile - Cập nhật profile của user hiện tại
     */
    public CompletionStage<Result> updateProfile(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TokenService.TokenPayload payload = requireAuth(request);
                
                JsonNode json = request.body().asJson();
                if (json == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
                }
                
                UpdateProfileRequest updateReq = Json.fromJson(json, UpdateProfileRequest.class);
                
                String ipAddress = request.remoteAddress();
                ProfileResponse profile = profileService.updateProfile(
                    payload.getUserId(), 
                    updateReq, 
                    ipAddress
                );
                
                return ok(Json.toJson(profile));
            } catch (ApiException e) {
                ObjectNode error = Json.newObject();
                error.put("status", e.getStatus());
                error.put("code", e.getCode());
                error.put("message", e.getMessage());
                return status(e.getStatus(), error);
            }
        }, dbContext.current());
    }

    /**
     * PUT /api/auth/change-password - Đổi mật khẩu
     */
    @RateLimited(RateLimited.Policy.CHANGE_PASSWORD)
    public CompletionStage<Result> changePassword(Http.Request request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TokenService.TokenPayload payload = requireAuth(request);
                
                JsonNode json = request.body().asJson();
                if (json == null) {
                    throw ApiException.badRequest("VALIDATION_FAILED", "Request body is required");
                }
                
                ChangePasswordRequest changeReq = Json.fromJson(json, ChangePasswordRequest.class);
                
                String ipAddress = request.remoteAddress();
                profileService.changePassword(payload.getUserId(), changeReq, ipAddress);
                
                return ok(Json.toJson(java.util.Map.of(
                    "message", "Password changed successfully",
                    "note", "All refresh tokens have been revoked. Please login again on other devices."
                )));
            } catch (ApiException e) {
                ObjectNode error = Json.newObject();
                error.put("status", e.getStatus());
                error.put("code", e.getCode());
                error.put("message", e.getMessage());
                return status(e.getStatus(), error);
            }
        }, dbContext.current());
    }

    /**
     * Trích xuất và xác thực token
     */
    private TokenService.TokenPayload requireAuth(Http.Request request) {
        String token = request.header("Authorization")
            .filter(h -> h.startsWith("Bearer "))
            .map(h -> h.substring(7))
            .orElseThrow(() -> ApiException.unauthorized("AUTH_TOKEN_INVALID", 
                "Missing or invalid authorization header"));
        
        return authService.verifyAndCheckStatus(token);
    }
}

package controllers;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

/**
 * ============================================================================
 * AUTH CONTROLLER
 * ============================================================================
 * 
 * Authentication service endpoints - STATELESS design
 * 
 * Đợt 4: Stateless validation (không dùng session local, mỗi request độc lập)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class AuthController extends Controller {
    
    /**
     * Login endpoint
     * 
     * Stateless: client phải gửi username + password trong request body
     * Không store session trên server
     */
    public Result login(Http.Request request) {
        Logger.info("[AuthController] Login request");
        
        try {
            // Lấy body (JSON)
            com.fasterxml.jackson.databind.JsonNode body = request.body().asJson();
            if (body == null) {
                return badRequest("{\"error\": \"Missing JSON body\"}");
            }
            
            String username = body.get("username") != null ? body.get("username").asText() : null;
            String password = body.get("password") != null ? body.get("password").asText() : null;
            
            if (username == null || password == null) {
                return badRequest("{\"error\": \"Missing username or password\"}");
            }
            
            // Validate (stateless - không rely on session)
            if (validateCredentials(username, password)) {
                // Trả JWT token (thay vì session cookie)
                String token = generateToken(username);
                
                Logger.info("[AuthController] ✓ Login success for user: " + username);
                return ok("{\"token\": \"" + token + "\", \"username\": \"" + username + "\"}");
            } else {
                Logger.warn("[AuthController] ✗ Invalid credentials for user: " + username);
                return unauthorized("{\"error\": \"Invalid credentials\"}");
            }
            
        } catch (Exception e) {
            Logger.error("[AuthController] Error: " + e.getMessage(), e);
            return internalServerError("{\"error\": \"Server error\"}");
        }
    }
    
    /**
     * Get user profile - phải pass token
     * 
     * Stateless: request phải có Authorization header với JWT token
     */
    public Result getProfile(Http.Request request) {
        Logger.info("[AuthController] GetProfile request");
        
        try {
            // Lấy token từ Authorization header
            String authHeader = request.header("Authorization").orElse(null);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized("{\"error\": \"Missing or invalid Authorization header\"}");
            }
            
            String token = authHeader.substring("Bearer ".length());
            
            // Verify token (stateless - validate token instead of checking session)
            String username = verifyToken(token);
            if (username == null) {
                Logger.warn("[AuthController] ✗ Invalid token");
                return unauthorized("{\"error\": \"Invalid or expired token\"}");
            }
            
            Logger.info("[AuthController] ✓ Valid token for user: " + username);
            return ok("{\"username\": \"" + username + "\", \"email\": \"" + username + "@example.com\"}");
            
        } catch (Exception e) {
            Logger.error("[AuthController] Error: " + e.getMessage(), e);
            return internalServerError("{\"error\": \"Server error\"}");
        }
    }
    
    /**
     * Validate credentials (simple example - in production use bcrypt + database)
     */
    private boolean validateCredentials(String username, String password) {
        // TODO: Implement proper validation with database
        // For now: simple hardcoded check
        return username.equals("testuser") && password.equals("testpass");
    }
    
    /**
     * Generate JWT token (simple example)
     */
    private String generateToken(String username) {
        // TODO: Implement proper JWT generation
        // For now: simple token (in production use JWT library like "com.auth0:java-jwt")
        return "jwt_token_for_" + username + "_" + System.currentTimeMillis();
    }
    
    /**
     * Verify JWT token (simple example)
     */
    private String verifyToken(String token) {
        // TODO: Implement proper JWT verification
        // For now: simple token check
        if (token.startsWith("jwt_token_for_") && token.contains("_")) {
            String[] parts = token.split("_");
            if (parts.length >= 4) {
                return parts[3]; // Extract username
            }
        }
        return null;
    }
}

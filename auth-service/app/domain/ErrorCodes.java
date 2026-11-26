package domain;

/**
 * Hằng số mã lỗi cho API response
 */
public final class ErrorCodes {
    // Xác thực
    public static final String AUTH_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";
    public static final String AUTH_ACCOUNT_LOCKED = "AUTH_ACCOUNT_LOCKED";
    public static final String AUTH_ACCOUNT_INACTIVE = "AUTH_ACCOUNT_INACTIVE";
    public static final String AUTH_ROLE_INACTIVE = "AUTH_ROLE_INACTIVE";
    public static final String AUTH_TOKEN_INVALID = "AUTH_TOKEN_INVALID";
    public static final String AUTH_TOKEN_EXPIRED = "AUTH_TOKEN_EXPIRED";
    public static final String AUTH_TOKEN_REVOKED = "AUTH_TOKEN_REVOKED";
    public static final String AUTH_REFRESH_EXPIRED = "AUTH_REFRESH_EXPIRED";
    public static final String AUTH_REFRESH_INVALID = "AUTH_REFRESH_INVALID";
    public static final String AUTH_REFRESH_REVOKED = "AUTH_REFRESH_REVOKED";
    
    // Quản lý người dùng
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String USER_USERNAME_EXISTS = "USER_USERNAME_EXISTS";
    public static final String USER_EMAIL_EXISTS = "USER_EMAIL_EXISTS";
    public static final String USER_CANNOT_LOCK_SELF = "USER_CANNOT_LOCK_SELF";
    public static final String USER_CANNOT_LOCK_LAST_ADMIN = "USER_CANNOT_LOCK_LAST_ADMIN";
    public static final String USER_WEAK_PASSWORD = "USER_WEAK_PASSWORD";
    public static final String USER_CURRENT_PASSWORD_INCORRECT = "USER_CURRENT_PASSWORD_INCORRECT";
    public static final String USER_PASSWORD_CONFIRM_MISMATCH = "USER_PASSWORD_CONFIRM_MISMATCH";
    public static final String USER_NEW_PASSWORD_SAME_AS_OLD = "USER_NEW_PASSWORD_SAME_AS_OLD";
    
    // Phase 4 - User Management
    public static final String PASSWORD_WEAK = "PASSWORD_WEAK";
    public static final String CANNOT_LOCK_SELF = "CANNOT_LOCK_SELF";
    public static final String CANNOT_LOCK_LAST_ADMIN = "CANNOT_LOCK_LAST_ADMIN";
    public static final String CANNOT_DELETE_SELF = "CANNOT_DELETE_SELF";
    public static final String CANNOT_DELETE_LAST_ADMIN = "CANNOT_DELETE_LAST_ADMIN";
    public static final String ROLE_INACTIVE = "ROLE_INACTIVE";
    
    // Chi nhánh
    public static final String BRANCH_NOT_FOUND = "BRANCH_NOT_FOUND";
    public static final String BRANCH_NOT_ACCESSIBLE = "BRANCH_NOT_ACCESSIBLE";
    public static final String BRANCH_INACTIVE = "BRANCH_INACTIVE";
    
    // Vai trò & Quyền hạn
    public static final String ROLE_NOT_FOUND = "ROLE_NOT_FOUND";
    public static final String ROLE_CODE_EXISTS = "ROLE_CODE_EXISTS";
    public static final String ROLE_IN_USE = "ROLE_IN_USE";
    public static final String PERMISSION_NOT_FOUND = "PERMISSION_NOT_FOUND";
    public static final String PERMISSION_CODE_EXISTS = "PERMISSION_CODE_EXISTS";
    public static final String PERMISSION_IN_USE = "PERMISSION_IN_USE";
    public static final String PERMISSION_CANNOT_REMOVE_FROM_ADMIN = "PERMISSION_CANNOT_REMOVE_FROM_ADMIN";
    
    // Chung
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    
    private ErrorCodes() {}
}

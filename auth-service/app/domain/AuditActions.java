package domain;

/**
 * Hằng số mã hành động audit (action code)
 */
public final class AuditActions {
    // Xác thực
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_AUTO_LOCKED = "ACCOUNT_AUTO_LOCKED";
    public static final String REFRESH_TOKEN_REUSE = "REFRESH_TOKEN_REUSE";
    public static final String LOGOUT_SUCCESS = "LOGOUT_SUCCESS";
    public static final String LOGOUT_ALL_SUCCESS = "LOGOUT_ALL_SUCCESS";
    
    // Quản lý người dùng
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String USER_LOCKED = "USER_LOCKED";
    public static final String USER_UNLOCKED = "USER_UNLOCKED";
    public static final String USER_DEACTIVATED = "USER_DEACTIVATED";
    public static final String USER_ACTIVATED = "USER_ACTIVATED";
    public static final String USER_ROLE_CHANGED = "USER_ROLE_CHANGED";
    public static final String USER_BRANCHES_CHANGED = "USER_BRANCHES_CHANGED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    
    // Chọn chi nhánh
    public static final String BRANCH_SELECTED = "BRANCH_SELECTED";
    
    // RBAC - Phân quyền
    public static final String ROLE_CREATED = "ROLE_CREATED";
    public static final String ROLE_UPDATED = "ROLE_UPDATED";
    public static final String ROLE_DEACTIVATED = "ROLE_DEACTIVATED";
    public static final String PERMISSION_CREATED = "PERMISSION_CREATED";
    public static final String PERMISSION_UPDATED = "PERMISSION_UPDATED";
    public static final String ROLE_PERMISSIONS_CHANGED = "ROLE_PERMISSIONS_CHANGED";
    
    private AuditActions() {}
}

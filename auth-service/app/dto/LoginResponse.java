package dto;

import java.util.List;

/**
 * Response DTO cho login thành công
 */
public class LoginResponse {
    private String accessToken;
    private int expiresIn; // seconds
    private String refreshToken;
    private UserInfo user;
    
    public LoginResponse() {}
    
    public LoginResponse(String accessToken, int expiresIn, String refreshToken, UserInfo user) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.refreshToken = refreshToken;
        this.user = user;
    }
    
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    
    public int getExpiresIn() { return expiresIn; }
    public void setExpiresIn(int expiresIn) { this.expiresIn = expiresIn; }
    
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    
    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }
    
    /**
     * Thông tin user trong response
     */
    public static class UserInfo {
        private Integer userId;
        private String username;
        private String fullName;
        private String roleCode;
        private String roleName;
        private List<Integer> branchIds;
        private Integer currentBranchId;
        private List<String> permissions;
        
        public UserInfo() {}
        
        public Integer getUserId() { return userId; }
        public void setUserId(Integer userId) { this.userId = userId; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        
        public String getRoleCode() { return roleCode; }
        public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
        
        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        
        public List<Integer> getBranchIds() { return branchIds; }
        public void setBranchIds(List<Integer> branchIds) { this.branchIds = branchIds; }
        
        public Integer getCurrentBranchId() { return currentBranchId; }
        public void setCurrentBranchId(Integer currentBranchId) { this.currentBranchId = currentBranchId; }
        
        public List<String> getPermissions() { return permissions; }
        public void setPermissions(List<String> permissions) { this.permissions = permissions; }
    }
}

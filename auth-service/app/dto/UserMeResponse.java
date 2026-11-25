package dto;

import java.util.List;

/**
 * Response DTO cho /api/auth/me
 */
public class UserMeResponse {
    private Integer userId;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String roleCode;
    private String roleName;
    private List<Integer> branchIds;
    private Integer currentBranchId;
    private List<String> permissions;
    
    public UserMeResponse() {}
    
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
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

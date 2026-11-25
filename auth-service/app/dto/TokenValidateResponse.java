package dto;

import java.util.List;

/**
 * Response DTO cho /api/auth/validate (introspection)
 */
public class TokenValidateResponse {
    private boolean active;
    private Integer userId;
    private String roleCode;
    private List<Integer> branchIds;
    private Integer currentBranchId;
    
    public TokenValidateResponse() {}
    
    public TokenValidateResponse(boolean active) {
        this.active = active;
    }
    
    public TokenValidateResponse(boolean active, Integer userId, String roleCode, 
                                List<Integer> branchIds, Integer currentBranchId) {
        this.active = active;
        this.userId = userId;
        this.roleCode = roleCode;
        this.branchIds = branchIds;
        this.currentBranchId = currentBranchId;
    }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    
    public List<Integer> getBranchIds() { return branchIds; }
    public void setBranchIds(List<Integer> branchIds) { this.branchIds = branchIds; }
    
    public Integer getCurrentBranchId() { return currentBranchId; }
    public void setCurrentBranchId(Integer currentBranchId) { this.currentBranchId = currentBranchId; }
}

package dto;

import javax.validation.constraints.*;
import java.util.List;

/**
 * Request DTO để gán role + branches cho user
 */
public class UpdateRoleBranchesRequest {
    @NotNull(message = "Role ID is required")
    private Integer roleId;
    
    @NotNull(message = "Branch IDs are required")
    @Size(min = 1, message = "At least one branch is required")
    private List<Integer> branchIds;

    // Getters and Setters
    public Integer getRoleId() {
        return roleId;
    }

    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }

    public List<Integer> getBranchIds() {
        return branchIds;
    }

    public void setBranchIds(List<Integer> branchIds) {
        this.branchIds = branchIds;
    }
}

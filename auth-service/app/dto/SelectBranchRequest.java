package dto;

import javax.validation.constraints.NotNull;

/**
 * Request DTO cho chọn chi nhánh
 */
public class SelectBranchRequest {
    @NotNull(message = "Branch ID is required")
    private Integer branchId;
    
    public SelectBranchRequest() {}
    
    public Integer getBranchId() {
        return branchId;
    }
    
    public void setBranchId(Integer branchId) {
        this.branchId = branchId;
    }
}

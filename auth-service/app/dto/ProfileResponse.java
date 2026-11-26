package dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Đối tượng dữ liệu phản hồi hồ sơ người dùng
 */
public class ProfileResponse {
    @JsonProperty("userId")
    private Integer userId;
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("fullName")
    private String fullName;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("phone")
    private String phone;
    
    @JsonProperty("roleId")
    private Integer roleId;
    
    @JsonProperty("roleCode")
    private String roleCode;
    
    @JsonProperty("roleName")
    private String roleName;
    
    @JsonProperty("branchIds")
    private java.util.List<Integer> branchIds;
    
    @JsonProperty("isActive")
    private Boolean isActive;
    
    @JsonProperty("isLocked")
    private Boolean isLocked;
    
    @JsonProperty("createdAt")
    private java.time.Instant createdAt;
    
    @JsonProperty("updatedAt")
    private java.time.Instant updatedAt;

    // Getters and Setters
    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getRoleId() {
        return roleId;
    }

    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public java.util.List<Integer> getBranchIds() {
        return branchIds;
    }

    public void setBranchIds(java.util.List<Integer> branchIds) {
        this.branchIds = branchIds;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Boolean getIsLocked() {
        return isLocked;
    }

    public void setIsLocked(Boolean isLocked) {
        this.isLocked = isLocked;
    }

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.Instant createdAt) {
        this.createdAt = createdAt;
    }

    public java.time.Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

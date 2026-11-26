package dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Đối tượng dữ liệu phản hồi Permission
 */
public class PermissionResponse {
    @JsonProperty("permissionId")
    private Integer permissionId;
    
    @JsonProperty("permissionCode")
    private String permissionCode;
    
    @JsonProperty("permissionName")
    private String permissionName;
    
    @JsonProperty("createdAt")
    private java.time.Instant createdAt;

    // Getters and Setters
    public Integer getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Integer permissionId) {
        this.permissionId = permissionId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public void setPermissionName(String permissionName) {
        this.permissionName = permissionName;
    }

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.Instant createdAt) {
        this.createdAt = createdAt;
    }
}

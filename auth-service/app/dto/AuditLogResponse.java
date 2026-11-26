package dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Đối tượng dữ liệu phản hồi nhật ký kiểm toán
 */
public class AuditLogResponse {
    @JsonProperty("auditLogId")
    private Long auditLogId;
    
    @JsonProperty("userId")
    private Integer userId;
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("branchId")
    private Integer branchId;
    
    @JsonProperty("actionCode")
    private String actionCode;
    
    @JsonProperty("entityName")
    private String entityName;
    
    @JsonProperty("entityId")
    private Long entityId;
    
    @JsonProperty("details")
    private String details;
    
    @JsonProperty("createdAt")
    private java.time.Instant createdAt;

    // Getters and Setters
    public Long getAuditLogId() {
        return auditLogId;
    }

    public void setAuditLogId(Long auditLogId) {
        this.auditLogId = auditLogId;
    }

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

    public Integer getBranchId() {
        return branchId;
    }

    public void setBranchId(Integer branchId) {
        this.branchId = branchId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.Instant createdAt) {
        this.createdAt = createdAt;
    }
}

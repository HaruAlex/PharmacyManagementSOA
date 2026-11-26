package dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Đối tượng dữ liệu yêu cầu tạo Role mới
 */
public class CreateRoleRequest {
    private String code;
    private String name;
    private String description;
    private Boolean isActive;

    // Getters and Setters
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}

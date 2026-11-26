package dto;

/**
 * Đối tượng dữ liệu yêu cầu cập nhật Permission
 * PermissionCode không được đổi (bất biến)
 */
public class UpdatePermissionRequest {
    private String name;
    private String description;
    private String category;

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}

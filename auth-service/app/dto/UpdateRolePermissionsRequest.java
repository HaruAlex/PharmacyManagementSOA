package dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Đối tượng dữ liệu yêu cầu cập nhật quyền của vai trò
 * Thay thế toàn bộ quyền, không phải thêm hoặc xóa riêng lẻ
 */
public class UpdateRolePermissionsRequest {
    @JsonProperty("permissionIds")
    private List<Integer> permissionIds;

    // Getters and Setters
    public List<Integer> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(List<Integer> permissionIds) {
        this.permissionIds = permissionIds;
    }
}

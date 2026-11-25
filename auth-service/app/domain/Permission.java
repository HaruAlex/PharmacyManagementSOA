package domain;

/**
 * Entity Permission - Quyền hạn trong hệ thống
 */
public class Permission {
    private Integer permissionId;
    private String permissionCode;
    private String permissionName;

    public Permission() {}

    public Permission(Integer permissionId, String permissionCode, String permissionName) {
        this.permissionId = permissionId;
        this.permissionCode = permissionCode;
        this.permissionName = permissionName;
    }

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
}

package domain;

/**
 * Thực thể Role - Vai trò người dùng trong hệ thống
 */
public class Role {
    private Integer roleId;
    private String roleCode;
    private String roleName;
    private boolean isActive;

    public Role() {}

    public Role(Integer roleId, String roleCode, String roleName, boolean isActive) {
        this.roleId = roleId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.isActive = isActive;
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

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}

package repositories;

import java.util.List;

/**
 * Repository interface cho RolePermissions (mapping table)
 */
public interface RolePermissionRepository {
    /**
     * Thay thế toàn bộ permission của role trong 1 transaction
     */
    void replacePermissions(Integer roleId, List<Integer> permissionIds);
    
    /**
     * Xóa tất cả permission của role
     */
    void deleteByRoleId(Integer roleId);
}

package repositories;

import domain.Permission;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface cho Permission entity
 */
public interface PermissionRepository {
    Optional<Permission> getById(Integer permissionId);
    Optional<Permission> getByCode(String permissionCode);
    List<Permission> getAll();
    List<Permission> getByRoleId(Integer roleId);
    List<Permission> getByUserId(Integer userId);
    boolean existsByCode(String permissionCode);
    
    Integer create(Permission permission);
    void update(Permission permission);
    
    /**
     * Kiểm tra permission có đang được gán cho role nào không
     */
    boolean isAssignedToAnyRole(Integer permissionId);
    
    /**
     * Thay thế toàn bộ quyền của vai trò trong một giao dịch nguyên tử.
     */
    void replaceRolePermissions(Integer roleId, List<Integer> permissionIds);
}

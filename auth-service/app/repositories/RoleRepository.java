package repositories;

import domain.Role;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface cho Role entity
 */
public interface RoleRepository {
    Optional<Role> getById(Integer roleId);
    Optional<Role> getByCode(String roleCode);
    List<Role> getAll();
    List<Role> getAllActive();
    boolean existsByCode(String roleCode);
    
    Integer create(Role role);
    void update(Role role);
    void setActive(Integer roleId, boolean active);
    
    /**
     * Kiểm tra role có đang được sử dụng bởi user active không
     */
    boolean isInUseByActiveUsers(Integer roleId);
}

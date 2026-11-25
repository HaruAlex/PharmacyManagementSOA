package repositories;

import domain.User;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface cho User entity
 */
public interface UserRepository {
    Optional<User> getById(Integer userId);
    Optional<User> getByUsername(String username);
    Optional<User> getByEmail(String email);
    Optional<User> getByUsernameOrEmail(String usernameOrEmail);
    boolean existsUsername(String username);
    boolean existsEmail(String email);
    
    /**
     * Tìm kiếm user với phân trang và filter
     */
    List<User> search(String nameFilter, String emailFilter, Integer roleId, 
                      Boolean isActive, Boolean isLocked,
                      int page, int pageSize, String sortBy, String sortOrder);
    
    long count(String nameFilter, String emailFilter, Integer roleId, 
               Boolean isActive, Boolean isLocked);
    
    Integer create(User user);
    void update(User user);
    void setLocked(Integer userId, boolean locked);
    void updatePasswordHash(Integer userId, String passwordHash);
    void incrementFailedLogin(Integer userId);
    void resetFailedLogin(Integer userId);
    void setActive(Integer userId, boolean active);
    
    /**
     * Đếm số Admin còn active và không bị locked
     */
    long countActiveAdmins();
}

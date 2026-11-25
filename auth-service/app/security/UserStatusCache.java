package security;

import com.typesafe.config.Config;
import play.cache.SyncCacheApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;

/**
 * Cache trạng thái user để tránh query DB mỗi request
 * Cache tối đa 10 giây, có thể clear chủ động khi thay đổi trạng thái
 */
@Singleton
public class UserStatusCache {
    private final SyncCacheApi cache;
    private final int cacheSeconds;
    
    @Inject
    public UserStatusCache(SyncCacheApi cache, Config config) {
        this.cache = cache;
        this.cacheSeconds = config.getInt("auth.userStatusCacheSeconds");
    }
    
    /**
     * Lấy trạng thái user từ cache
     */
    public UserStatus get(Integer userId) {
        java.util.Optional<Object> cached = cache.get(cacheKey(userId));
        if (cached.isPresent() && cached.get() instanceof UserStatus) {
            return (UserStatus) cached.get();
        }
        return null;
    }
    
    /**
     * Lưu trạng thái user vào cache
     */
    public void put(Integer userId, UserStatus status) {
        if (cacheSeconds > 0) {
            cache.set(cacheKey(userId), status, cacheSeconds);
        }
    }
    
    /**
     * Xóa cache của user (khi khóa, đổi role, đổi password...)
     */
    public void evict(Integer userId) {
        cache.remove(cacheKey(userId));
    }
    
    /**
     * Xóa cache của nhiều users (khi đổi quyền của role)
     */
    public void evictMultiple(java.util.List<Integer> userIds) {
        for (Integer userId : userIds) {
            evict(userId);
        }
    }
    
    private String cacheKey(Integer userId) {
        return "user_status:" + userId;
    }
    
    /**
     * DTO chứa trạng thái user
     */
    public static class UserStatus {
        private boolean isActive;
        private boolean isLocked;
        private Integer roleId;
        private String roleCode;
        
        public UserStatus() {}
        
        public UserStatus(boolean isActive, boolean isLocked, Integer roleId, String roleCode) {
            this.isActive = isActive;
            this.isLocked = isLocked;
            this.roleId = roleId;
            this.roleCode = roleCode;
        }
        
        public boolean isActive() { return isActive; }
        public void setActive(boolean active) { isActive = active; }
        
        public boolean isLocked() { return isLocked; }
        public void setLocked(boolean locked) { isLocked = locked; }
        
        public Integer getRoleId() { return roleId; }
        public void setRoleId(Integer roleId) { this.roleId = roleId; }
        
        public String getRoleCode() { return roleCode; }
        public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
        
        public boolean isValid() {
            return isActive && !isLocked;
        }
    }
}

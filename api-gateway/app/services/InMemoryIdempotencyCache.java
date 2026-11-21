package services;

import play.Logger;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 * IN-MEMORY IDEMPOTENCY CACHE
 * ============================================================================
 * 
 * Simple in-memory implementation cho dev/test.
 * Để production, thay bằng Redis implementation.
 * 
 * Đợt 4: Chịu lỗi + An toàn (Fault Tolerance)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class InMemoryIdempotencyCache implements IdempotencyCache {
    
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    
    // Background cleanup thread (xóa expired entries mỗi 1 phút)
    static {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60000); // 1 minute
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).setDaemon(true);
    }
    
    @Override
    public CachedResponse get(String key) {
        CacheEntry entry = cache.get(key);
        
        if (entry == null) {
            return null;
        }
        
        // Kiểm tra expiration
        if (System.currentTimeMillis() > entry.expiresAt) {
            Logger.info("[IdempotencyCache] Cache entry expired for key: " + key);
            cache.remove(key);
            return null;
        }
        
        Logger.debug("[IdempotencyCache] Cache HIT for key: " + key);
        return entry.response;
    }
    
    @Override
    public void put(String key, CachedResponse response, int ttlSeconds) {
        long expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000L);
        response.expiresAt = expiresAt;
        
        CacheEntry entry = new CacheEntry(response, expiresAt);
        cache.put(key, entry);
        
        Logger.info("[IdempotencyCache] Cached key: " + key + " (TTL: " + ttlSeconds + "s)");
    }
    
    @Override
    public void remove(String key) {
        cache.remove(key);
        Logger.debug("[IdempotencyCache] Removed key: " + key);
    }
    
    /**
     * Cleanup expired entries (gọi periodically)
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (now > entry.getValue().expiresAt) {
                cache.remove(entry.getKey());
                removed++;
            }
        }
        
        if (removed > 0) {
            Logger.info("[IdempotencyCache] Cleaned up " + removed + " expired entries");
        }
    }
    
    /**
     * Cache entry wrapper
     */
    private static class CacheEntry {
        CachedResponse response;
        long expiresAt;
        
        CacheEntry(CachedResponse response, long expiresAt) {
            this.response = response;
            this.expiresAt = expiresAt;
        }
    }
}

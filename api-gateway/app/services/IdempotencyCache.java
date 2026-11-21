package services;

import java.util.Map;

/**
 * ============================================================================
 * IDEMPOTENCY CACHE INTERFACE
 * ============================================================================
 * 
 * Cache idempotency keys để phòng chống duplicate request.
 * Đợt 4: Chịu lỗi + An toàn (Fault Tolerance)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public interface IdempotencyCache {
    
    /**
     * Lấy cached response bằng idempotency key
     * 
     * @param key Idempotency-Key
     * @return CachedResponse hoặc null nếu không tìm thấy
     */
    CachedResponse get(String key);
    
    /**
     * Lưu response vào cache
     * 
     * @param key Idempotency-Key
     * @param response Response cần cache
     * @param ttlSeconds TTL (Time-To-Live) tính bằng giây
     */
    void put(String key, CachedResponse response, int ttlSeconds);
    
    /**
     * Xóa cache entry
     */
    void remove(String key);
    
    /**
     * Cached response
     */
    class CachedResponse {
        public int statusCode;
        public String body;
        public Map<String, String> headers;
        public long expiresAt; // System.currentTimeMillis() khi expire
        
        public CachedResponse(int statusCode, String body, Map<String, String> headers) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
            this.expiresAt = Long.MAX_VALUE; // Sẽ được set bởi implementation
        }
    }
}

package services;

import play.Logger;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 * BULKHEAD REGISTRY
 * ============================================================================
 * 
 * Quản lý 1 thread pool cho mỗi service.
 * Singleton để tất cả request chia sẻ cùng pool.
 * 
 * Đợt 5: Resilience (Bulkhead)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class BulkheadRegistry {
    
    private final Map<String, BulkheadExecutor> executors = new ConcurrentHashMap<>();
    private static final int CORE_POOL_SIZE = 10;
    private static final int MAX_QUEUE_SIZE = 5;
    
    /**
     * Get all bulkheads for metrics collection
     */
    public Map<String, BulkheadExecutor> getAllBulkheads() {
        return new ConcurrentHashMap<>(executors);
    }
    
    /**
     * Lấy hoặc tạo bulkhead executor cho service
     * 
     * @param serviceName Tên service
     * @return BulkheadExecutor instance
     */
    public BulkheadExecutor getOrCreate(String serviceName) {
        return executors.computeIfAbsent(serviceName, name -> {
            Logger.info("[BulkheadRegistry] Creating bulkhead for: " + name);
            return new BulkheadExecutor(name, CORE_POOL_SIZE, MAX_QUEUE_SIZE);
        });
    }
    
    /**
     * Get bulkhead stats
     */
    public void logMetrics() {
        executors.forEach((name, executor) -> {
            BulkheadExecutor.BulkheadStats stats = executor.getStats();
            if (stats != null) {
                Logger.info("[BulkheadRegistry] " + stats);
            }
        });
    }
    
    /**
     * Shutdown all executors (graceful shutdown)
     */
    public void shutdownAll() {
        executors.forEach((name, executor) -> {
            executor.shutdown();
        });
        Logger.info("[BulkheadRegistry] All bulkheads shutdown");
    }
}

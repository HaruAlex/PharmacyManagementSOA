package services;

import play.Logger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ============================================================================
 * BULKHEAD EXECUTOR
 * ============================================================================
 * 
 * Thread pool isolator - mỗi service có thread pool riêng.
 * Tránh resource starvation khi 1 service gặp vấn đề.
 * 
 * Config:
 * - Core threads: 10
 * - Queue size: 5 (reject when full)
 * - Keep alive: 60 seconds
 * - Policy: ABORT (reject when queue full)
 * 
 * Đợt 5: Resilience (Bulkhead)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class BulkheadExecutor {
    
    private final String serviceName;
    private final ExecutorService executor;
    private final int corePoolSize;
    private final int maxQueueSize;
    private final AtomicInteger rejectedCount = new AtomicInteger(0);
    
    public BulkheadExecutor(String serviceName, int corePoolSize, int maxQueueSize) {
        this.serviceName = serviceName;
        this.corePoolSize = corePoolSize;
        this.maxQueueSize = maxQueueSize;
        
        // Create thread pool với queue rejection policy
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(maxQueueSize);
        RejectedExecutionHandler rejectPolicy = new ThreadPoolExecutor.AbortPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                rejectedCount.incrementAndGet();
                Logger.warn("[BulkheadExecutor] " + serviceName + " - Task rejected! " +
                           "Active=" + e.getActiveCount() + ", Queue=" + e.getQueue().size());
                throw new RejectedExecutionException("Bulkhead queue full for " + serviceName);
            }
        };
        
        this.executor = new ThreadPoolExecutor(
            corePoolSize,           // core threads
            corePoolSize,           // max threads (fixed size)
            60,                     // keep alive time
            TimeUnit.SECONDS,       // keep alive unit
            queue,                  // bounded queue
            new ThreadFactory() {   // named threads for debugging
                private final AtomicInteger count = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("bulkhead-" + serviceName + "-" + count.incrementAndGet());
                    t.setDaemon(false);
                    return t;
                }
            },
            rejectPolicy            // abort on queue full
        );
        
        Logger.info("[BulkheadExecutor] Created for " + serviceName + 
                   " (pool=" + corePoolSize + ", queue=" + maxQueueSize + ")");
    }
    
    /**
     * Xử lý task với timeout
     * 
     * @param task Task to execute
     * @param timeoutMs Timeout (milliseconds)
     * @throws Exception nếu timeout hoặc rejected
     */
    public <T> T execute(Callable<T> task, long timeoutMs) throws Exception {
        try {
            Future<T> future = executor.submit(task);
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            Logger.error("[BulkheadExecutor] " + serviceName + " - Bulkhead rejected task");
            throw new RuntimeException("Bulkhead queue full for " + serviceName, e);
        } catch (TimeoutException e) {
            Logger.error("[BulkheadExecutor] " + serviceName + " - Task timeout after " + timeoutMs + "ms");
            throw new RuntimeException("Bulkhead task timeout for " + serviceName, e);
        }
    }
    
    /**
     * Get executor stats
     */
    public BulkheadStats getStats() {
        if (executor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
            return new BulkheadStats(
                serviceName,
                tpe.getActiveCount(),
                corePoolSize,
                tpe.getQueue().size(),
                maxQueueSize,
                tpe.getCompletedTaskCount(),
                rejectedCount.get()
            );
        }
        return null;
    }
    
    /**
     * Get active threads count (for metrics)
     */
    public int getActiveCount() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getActiveCount();
        }
        return 0;
    }
    
    /**
     * Get queue size (for metrics)
     */
    public int getQueueSize() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getQueue().size();
        }
        return 0;
    }
    
    /**
     * Get completed task count (for metrics)
     */
    public long getCompletedTaskCount() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getCompletedTaskCount();
        }
        return 0;
    }
    
    /**
     * Get rejected count (for metrics)
     */
    public int getRejectedCount() {
        return rejectedCount.get();
    }
    
    /**
     * Shutdown executor
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        Logger.info("[BulkheadExecutor] Shutdown: " + serviceName);
    }
    
    /**
     * Stats DTO
     */
    public static class BulkheadStats {
        public String serviceName;
        public int activeThreads;
        public int maxThreads;
        public int queueSize;
        public int maxQueueSize;
        public long completedTasks;
        public int rejectedTasks;
        
        public BulkheadStats(String serviceName, int activeThreads, int maxThreads,
                           int queueSize, int maxQueueSize, long completedTasks, int rejectedTasks) {
            this.serviceName = serviceName;
            this.activeThreads = activeThreads;
            this.maxThreads = maxThreads;
            this.queueSize = queueSize;
            this.maxQueueSize = maxQueueSize;
            this.completedTasks = completedTasks;
            this.rejectedTasks = rejectedTasks;
        }
        
        @Override
        public String toString() {
            return serviceName + " [Bulkhead] active=" + activeThreads + "/" + maxThreads +
                   " queue=" + queueSize + "/" + maxQueueSize + 
                   " completed=" + completedTasks + " rejected=" + rejectedTasks;
        }
    }
}

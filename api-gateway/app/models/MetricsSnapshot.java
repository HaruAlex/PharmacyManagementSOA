package models;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 * METRICS SNAPSHOT
 * ============================================================================
 * 
 * DTO for capturing system metrics at a point in time.
 * Đợt 6: Monitoring & Observability
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class MetricsSnapshot {
    
    public long timestamp;
    public JvmMetrics jvm;
    public Map<String, CircuitBreakerMetrics> circuitBreakers;
    public Map<String, BulkheadMetrics> bulkheads;
    public Map<String, RequestMetrics> requests;
    public long uptime;  // milliseconds
    
    public MetricsSnapshot() {
        this.timestamp = System.currentTimeMillis();
        this.circuitBreakers = new ConcurrentHashMap<>();
        this.bulkheads = new ConcurrentHashMap<>();
        this.requests = new ConcurrentHashMap<>();
    }
    
    /**
     * JVM Metrics
     */
    public static class JvmMetrics {
        public long heapUsedBytes;
        public long heapMaxBytes;
        public long nonHeapUsedBytes;
        public int threadCount;
        public int peakThreadCount;
        public long gcCollectionCount;
        
        public JvmMetrics() {
            Runtime rt = Runtime.getRuntime();
            this.heapUsedBytes = rt.totalMemory() - rt.freeMemory();
            this.heapMaxBytes = rt.maxMemory();
            this.nonHeapUsedBytes = 0;  // Would need MemoryMXBean for accurate value
            
            ThreadGroup root = Thread.currentThread().getThreadGroup();
            this.threadCount = root.activeCount();
            this.peakThreadCount = this.threadCount;
            this.gcCollectionCount = 0;
        }
    }
    
    /**
     * Circuit Breaker Metrics
     */
    public static class CircuitBreakerMetrics {
        public String service;
        public String state;  // CLOSED, OPEN, HALF_OPEN
        public int failures;
        public int successes;
        public int window;
        public long lastStateChangeMs;
        
        public CircuitBreakerMetrics(String service, String state) {
            this.service = service;
            this.state = state;
            this.failures = 0;
            this.successes = 0;
            this.window = 0;
            this.lastStateChangeMs = System.currentTimeMillis();
        }
    }
    
    /**
     * Bulkhead Metrics
     */
    public static class BulkheadMetrics {
        public String service;
        public int activeThreads;
        public int corePoolSize;
        public int queueSize;
        public int maxQueueSize;
        public long completedTasks;
        public int rejectedTasks;
        
        public BulkheadMetrics(String service) {
            this.service = service;
            this.activeThreads = 0;
            this.corePoolSize = 10;
            this.queueSize = 0;
            this.maxQueueSize = 5;
            this.completedTasks = 0;
            this.rejectedTasks = 0;
        }
    }
    
    /**
     * Request Metrics
     */
    public static class RequestMetrics {
        public String path;
        public String method;
        public long totalRequests;
        public long successRequests;  // 2xx
        public long failureRequests;  // 4xx, 5xx
        public long avgResponseTimeMs;
        
        public RequestMetrics(String method, String path) {
            this.method = method;
            this.path = path;
            this.totalRequests = 0;
            this.successRequests = 0;
            this.failureRequests = 0;
            this.avgResponseTimeMs = 0;
        }
    }
    
    /**
     * Get Prometheus-style text representation
     */
    public String toPrometheus() {
        StringBuilder sb = new StringBuilder();
        
        // Circuit Breaker Metrics
        sb.append("# HELP circuit_breaker_state Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)\n");
        sb.append("# TYPE circuit_breaker_state gauge\n");
        circuitBreakers.forEach((service, cb) -> {
            int stateValue = "CLOSED".equals(cb.state) ? 0 : ("OPEN".equals(cb.state) ? 1 : 2);
            sb.append("circuit_breaker_state{service=\"").append(service).append("\"} ").append(stateValue).append("\n");
        });
        
        sb.append("\n# HELP circuit_breaker_failures Total failures recorded\n");
        sb.append("# TYPE circuit_breaker_failures counter\n");
        circuitBreakers.forEach((service, cb) -> {
            sb.append("circuit_breaker_failures{service=\"").append(service).append("\"} ").append(cb.failures).append("\n");
        });
        
        // Bulkhead Metrics
        sb.append("\n# HELP bulkhead_active_threads Active threads in bulkhead pool\n");
        sb.append("# TYPE bulkhead_active_threads gauge\n");
        bulkheads.forEach((service, bh) -> {
            sb.append("bulkhead_active_threads{service=\"").append(service).append("\"} ").append(bh.activeThreads).append("\n");
        });
        
        sb.append("\n# HELP bulkhead_queue_size Current queue size\n");
        sb.append("# TYPE bulkhead_queue_size gauge\n");
        bulkheads.forEach((service, bh) -> {
            sb.append("bulkhead_queue_size{service=\"").append(service).append("\"} ").append(bh.queueSize).append("\n");
        });
        
        sb.append("\n# HELP bulkhead_rejected_tasks Total rejected tasks\n");
        sb.append("# TYPE bulkhead_rejected_tasks counter\n");
        bulkheads.forEach((service, bh) -> {
            sb.append("bulkhead_rejected_tasks{service=\"").append(service).append("\"} ").append(bh.rejectedTasks).append("\n");
        });
        
        // JVM Metrics
        sb.append("\n# HELP jvm_memory_used_bytes JVM memory used\n");
        sb.append("# TYPE jvm_memory_used_bytes gauge\n");
        sb.append("jvm_memory_used_bytes{type=\"heap\"} ").append(jvm.heapUsedBytes).append("\n");
        
        sb.append("\n# HELP jvm_memory_max_bytes JVM max memory\n");
        sb.append("# TYPE jvm_memory_max_bytes gauge\n");
        sb.append("jvm_memory_max_bytes{type=\"heap\"} ").append(jvm.heapMaxBytes).append("\n");
        
        sb.append("\n# HELP jvm_threads_current Current active threads\n");
        sb.append("# TYPE jvm_threads_current gauge\n");
        sb.append("jvm_threads_current ").append(jvm.threadCount).append("\n");
        
        sb.append("\n# HELP uptime_ms Application uptime in milliseconds\n");
        sb.append("# TYPE uptime_ms gauge\n");
        sb.append("uptime_ms ").append(uptime).append("\n");
        
        return sb.toString();
    }
    
    /**
     * Get JSON representation
     */
    public String toJson() {
        return com.google.gson.Gson.class.getSimpleName();  // Placeholder
    }
}

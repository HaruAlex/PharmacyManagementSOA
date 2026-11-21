package services;

import models.MetricsSnapshot;
import play.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * ============================================================================
 * METRICS COLLECTOR
 * ============================================================================
 * 
 * Collects metrics from Circuit Breaker + Bulkhead + JVM.
 * Đợt 6: Monitoring & Observability
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class MetricsCollector {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final long startTime;
    
    @Inject
    public MetricsCollector(CircuitBreakerRegistry circuitBreakerRegistry, BulkheadRegistry bulkheadRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.startTime = System.currentTimeMillis();
        Logger.info("[MetricsCollector] Initialized");
    }
    
    /**
     * Capture current metrics snapshot
     */
    public MetricsSnapshot captureSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        
        // Capture uptime
        snapshot.uptime = System.currentTimeMillis() - startTime;
        
        // Capture JVM metrics
        snapshot.jvm = new MetricsSnapshot.JvmMetrics();
        
        // Capture circuit breaker metrics
        if (circuitBreakerRegistry != null) {
            circuitBreakerRegistry.getAllCircuitBreakers().forEach((serviceName, cb) -> {
                MetricsSnapshot.CircuitBreakerMetrics cbMetrics = 
                    new MetricsSnapshot.CircuitBreakerMetrics(serviceName, cb.getState().toString());
                cbMetrics.failures = cb.getFailureCount();
                cbMetrics.successes = cb.getSuccessCount();
                snapshot.circuitBreakers.put(serviceName, cbMetrics);
            });
        }
        
        // Capture bulkhead metrics
        if (bulkheadRegistry != null) {
            bulkheadRegistry.getAllBulkheads().forEach((serviceName, bh) -> {
                MetricsSnapshot.BulkheadMetrics bhMetrics = new MetricsSnapshot.BulkheadMetrics(serviceName);
                bhMetrics.activeThreads = bh.getActiveCount();
                bhMetrics.queueSize = bh.getQueueSize();
                bhMetrics.completedTasks = bh.getCompletedTaskCount();
                bhMetrics.rejectedTasks = bh.getRejectedCount();
                snapshot.bulkheads.put(serviceName, bhMetrics);
            });
        }
        
        Logger.debug("[MetricsCollector] Snapshot captured");
        return snapshot;
    }
    
    /**
     * Get Prometheus format metrics
     */
    public String getPrometheusMetrics() {
        MetricsSnapshot snapshot = captureSnapshot();
        return snapshot.toPrometheus();
    }
}

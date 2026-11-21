package controllers;

import play.mvc.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import play.libs.Json;
import services.MetricsCollector;
import models.MetricsSnapshot;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

/**
 * ============================================================================
 * METRICS CONTROLLER
 * ============================================================================
 * 
 * Exposes /metrics (Prometheus), /healthz (detailed health), /health (simple health)
 * Đợt 6: Monitoring & Observability
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class MetricsController extends Controller {
    
    private final MetricsCollector metricsCollector;
    private final long startTime;
    
    @Inject
    public MetricsController(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * GET /metrics - Prometheus format
     */
    public Result metrics() {
        String prometheusMetrics = metricsCollector.getPrometheusMetrics();
        return ok(prometheusMetrics).as("text/plain; charset=utf-8");
    }
    
    /**
     * GET /healthz - Detailed health check (Kubernetes-style)
     */
    public Result healthz() {
        ObjectNode response = Json.newObject();
        
        try {
            MetricsSnapshot snapshot = metricsCollector.captureSnapshot();
            
            // Overall status
            response.put("status", "UP");
            response.put("service", "api-gateway");
            response.put("timestamp", Instant.now().toString());
            response.put("uptime_ms", snapshot.uptime);
            
            // JVM Health
            ObjectNode jvmHealth = Json.newObject();
            jvmHealth.put("status", "UP");
            jvmHealth.put("heap_used_mb", snapshot.jvm.heapUsedBytes / (1024 * 1024));
            jvmHealth.put("heap_max_mb", snapshot.jvm.heapMaxBytes / (1024 * 1024));
            jvmHealth.put("threads", snapshot.jvm.threadCount);
            jvmHealth.put("peak_threads", snapshot.jvm.peakThreadCount);
            response.set("jvm", jvmHealth);
            
            // Circuit Breaker Health
            ObjectNode cbHealth = Json.newObject();
            int openCircuits = 0;
            for (MetricsSnapshot.CircuitBreakerMetrics cb : snapshot.circuitBreakers.values()) {
                if ("OPEN".equals(cb.state)) {
                    openCircuits++;
                }
            }
            cbHealth.put("status", openCircuits > 0 ? "DEGRADED" : "UP");
            cbHealth.put("total", snapshot.circuitBreakers.size());
            cbHealth.put("open", openCircuits);
            ArrayNode cbDetails = Json.newArray();
            snapshot.circuitBreakers.forEach((service, cb) -> {
                ObjectNode detail = Json.newObject();
                detail.put("service", service);
                detail.put("state", cb.state);
                detail.put("failures", cb.failures);
                cbDetails.add(detail);
            });
            cbHealth.set("details", cbDetails);
            response.set("circuitBreakers", cbHealth);
            
            // Bulkhead Health
            ObjectNode bhHealth = Json.newObject();
            bhHealth.put("status", "UP");
            bhHealth.put("total", snapshot.bulkheads.size());
            ArrayNode bhDetails = Json.newArray();
            snapshot.bulkheads.forEach((service, bh) -> {
                ObjectNode detail = Json.newObject();
                detail.put("service", service);
                detail.put("active_threads", bh.activeThreads);
                detail.put("queue_size", bh.queueSize);
                detail.put("rejected_tasks", bh.rejectedTasks);
                bhDetails.add(detail);
            });
            bhHealth.set("details", bhDetails);
            response.set("bulkheads", bhHealth);
            
            return ok(response);
            
        } catch (Exception e) {
            response.put("status", "DOWN");
            response.put("error", e.getMessage());
            return status(503, response);
        }
    }
    
    /**
     * GET /health - Simple health check (Legacy)
     */
    public Result health() {
        ObjectNode response = Json.newObject();
        response.put("status", "UP");
        response.put("service", "api-gateway");
        return ok(response);
    }
}

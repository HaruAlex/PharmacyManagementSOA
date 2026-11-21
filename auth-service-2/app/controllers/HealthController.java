package controllers;

import play.mvc.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import javax.inject.Singleton;
import java.time.Instant;

/**
 * Controller xử lý Health Check cho Consul
 * 
 * Endpoint: GET /health
 * Consul sẽ gọi endpoint này mỗi 10 giây để kiểm tra service
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class HealthController extends Controller {
    
    /**
     * Endpoint kiểm tra sức khỏe cơ bản
     * 
     * @return HTTP 200 OK nếu service khỏe mạnh
     */
    public Result health() {
        ObjectNode response = Json.newObject();
        
        try {
            response.put("status", "healthy");
            response.put("service", "auth-service");
            response.put("timestamp", Instant.now().toString());
            return ok(response);
            
        } catch (Exception e) {
            response.put("status", "unhealthy");
            response.put("error", e.getMessage());
            return status(503, response);
        }
    }
    
    /**
     * Endpoint kiểm tra chi tiết (optional)
     * Trả về thông tin chi tiết về system metrics
     */
    public Result detailedHealth() {
        ObjectNode response = Json.newObject();
        response.put("status", "healthy");
        response.put("service", "auth-service");
        
        // Memory metrics
        ObjectNode memory = Json.newObject();
        Runtime runtime = Runtime.getRuntime();
        memory.put("used", runtime.totalMemory() - runtime.freeMemory());
        memory.put("free", runtime.freeMemory());
        memory.put("total", runtime.totalMemory());
        memory.put("max", runtime.maxMemory());
        response.set("memory", memory);
        
        // JVM info
        ObjectNode jvm = Json.newObject();
        jvm.put("version", System.getProperty("java.version"));
        jvm.put("vendor", System.getProperty("java.vendor"));
        response.set("jvm", jvm);
        
        return ok(response);
    }
}

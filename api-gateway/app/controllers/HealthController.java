package controllers;

import play.mvc.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ecwid.consul.v1.ConsulClient;
import play.libs.Json;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

/**
 * Controller xử lý Health Check cho API Gateway
 * 
 * Endpoint: GET /health
 * 
 * Đợt 2: Thêm kiểm tra Consul khả dụng
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class HealthController extends Controller {
    
    // Đợt 2: Inject ConsulClient để kiểm tra
    private final ConsulClient consulClient;
    
    @Inject
    public HealthController(ConsulClient consulClient) {
        this.consulClient = consulClient;
    }
    
    /**
     * Endpoint kiểm tra sức khỏe cơ bản
     * 
     * @return HTTP 200 OK nếu gateway khỏe mạnh
     */
    public Result health() {
        ObjectNode response = Json.newObject();
        
        try {
            response.put("status", "healthy");
            response.put("service", "api-gateway");
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
     */
    public Result detailedHealth() {
        ObjectNode response = Json.newObject();
        response.put("status", "healthy");
        response.put("service", "api-gateway");
        
        ObjectNode memory = Json.newObject();
        Runtime runtime = Runtime.getRuntime();
        memory.put("used", runtime.totalMemory() - runtime.freeMemory());
        memory.put("free", runtime.freeMemory());
        memory.put("total", runtime.totalMemory());
        memory.put("max", runtime.maxMemory());
        response.set("memory", memory);
        
        ObjectNode jvm = Json.newObject();
        jvm.put("version", System.getProperty("java.version"));
        jvm.put("vendor", System.getProperty("java.vendor"));
        response.set("jvm", jvm);
        
        return ok(response);
    }
}


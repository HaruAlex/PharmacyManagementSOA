package controllers;

import play.mvc.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.db.Database;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import com.ecwid.consul.v1.ConsulClient;

/**
 * Bộ điều khiển kiểm tra trạng thái Consul và cơ sở dữ liệu
 * 
 * Endpoint: GET /health - Kiểm tra cho Consul (mỗi 10s)
 * Consul sẽ gọi endpoint này để kiểm tra service health
 */
@Singleton
public class HealthController extends Controller {
    
    private final ConsulClient consulClient;
    private final Database database;
    
    @Inject
    public HealthController(ConsulClient consulClient, Database database) {
        this.consulClient = consulClient;
        this.database = database;
    }
    
    /**
     * Endpoint kiểm tra trạng thái cơ bản của Consul và cơ sở dữ liệu
     * 
     * @return HTTP 200 OK nếu service và database khỏe mạnh
     */
    public Result health() {
        ObjectNode response = Json.newObject();
        
        try {
            // Kiểm tra kết nối database
            boolean dbHealthy = checkDatabaseConnection();
            
            if (dbHealthy) {
                response.put("status", "healthy");
                response.put("service", "auth-service");
                response.put("instance", System.getProperty("service.instance.id", "auth-1"));
                response.put("database", "connected");
                response.put("timestamp", Instant.now().toString());
                return ok(response);
            } else {
                response.put("status", "unhealthy");
                response.put("database", "disconnected");
                return status(503, response);
            }
            
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
        
        // Trạng thái kết nối cơ sở dữ liệu
        response.put("database", checkDatabaseConnection() ? "connected" : "disconnected");
        
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
    
    /**
     * Kiểm tra kết nối database
     */
    private boolean checkDatabaseConnection() {
        try {
            return database.withConnection(conn -> {
                // Thử query đơn giản để kiểm tra kết nối
                try (var stmt = conn.createStatement();
                     var rs = stmt.executeQuery("SELECT 1")) {
                    return rs.next();
                }
            });
        } catch (Exception e) {
            return false;
        }
    }
}

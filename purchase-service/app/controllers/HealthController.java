package controllers;

import play.mvc.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.sql.Connection;
import play.db.Database;
import com.ecwid.consul.v1.ConsulClient;

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
    
    private final Database database;
    private final ConsulClient consulClient;
    
    @Inject
    public HealthController(Database database, ConsulClient consulClient) {
        this.database = database;
        this.consulClient = consulClient;
    }
    
    /**
     * Endpoint kiểm tra sức khỏe cơ bản
     * 
     * @return HTTP 200 OK nếu service khỏe mạnh
     *         HTTP 503 Service Unavailable nếu có vấn đề
     */
    public Result health() {
        ObjectNode response = Json.newObject();
        
        try {
            // Kiểm tra kết nối database
            boolean dbHealthy = kiemTraDatabase();
            
            if (dbHealthy) {
                response.put("status", "healthy");
                response.put("service", "purchase-service");
                response.put("instance", System.getProperty("service.instance.id", "purchase-1"));
                response.put("timestamp", Instant.now().toString());
                return ok(response);
            } else {
                response.put("status", "unhealthy");
                response.put("reason", "Database connection failed");
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
        
        // Database check
        ObjectNode database = Json.newObject();
        try {
            long startTime = System.currentTimeMillis();
            boolean dbOk = kiemTraDatabase();
            long responseTime = System.currentTimeMillis() - startTime;
            
            database.put("connected", dbOk);
            database.put("responseTime", responseTime + "ms");
        } catch (Exception e) {
            database.put("connected", false);
            database.put("error", e.getMessage());
        }
        response.set("database", database);
        
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
     * 
     * @return true nếu database OK, false nếu lỗi
     */
    private boolean kiemTraDatabase() {
        try (Connection conn = database.getConnection()) {
            return conn.isValid(3); // Timeout 3 giây
        } catch (Exception e) {
            return false;
        }
    }
}

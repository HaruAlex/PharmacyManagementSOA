package services;

import com.ecwid.consul.v1.health.model.HealthService;
import java.util.List;

/**
 * ============================================================================
 * LOAD BALANCER INTERFACE
 * ============================================================================
 * 
 * Interface cho các chiến lược cân bằng tải.
 * 
 * Đợt 3: Cân bằng tải (Load Balancing)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public interface LoadBalancer {
    
    /**
     * Chọn 1 instance từ danh sách để phục vụ request
     * 
     * @param instances Danh sách instance khỏe mạnh (passing)
     * @return Instance được chọn
     */
    HealthService selectInstance(List<HealthService> instances);
    
    /**
     * Lấy tên chiến lược cân bằng tải
     * @return Tên (vd: "round-robin")
     */
    String getStrategyName();
}

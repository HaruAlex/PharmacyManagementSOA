package services;

import com.ecwid.consul.v1.health.model.HealthService;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ============================================================================
 * ROUND-ROBIN LOAD BALANCER
 * ============================================================================
 * 
 * Cân bằng tải theo kiểu Round-robin: lần lượt chọn từng instance.
 * 
 * Ví dụ: [instance-1, instance-2, instance-3]
 * Request 1 -> instance-1
 * Request 2 -> instance-2
 * Request 3 -> instance-3
 * Request 4 -> instance-1 (quay lại đầu)
 * 
 * Đợt 3: Cân bằng tải (Load Balancing)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class RoundRobinLoadBalancer implements LoadBalancer {
    
    // Bộ đếm Round-robin (thread-safe)
    private final AtomicInteger counter = new AtomicInteger(0);
    
    /**
     * Chọn instance tiếp theo theo kiểu Round-robin
     */
    @Override
    public HealthService selectInstance(List<HealthService> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        
        // Lấy chỉ số tiếp theo
        int nextIndex = counter.getAndIncrement() % instances.size();
        
        // Đảm bảo counter không overflow (reset về 0 nếu quá lớn)
        if (counter.get() > Integer.MAX_VALUE / 2) {
            counter.set(0);
        }
        
        return instances.get(nextIndex);
    }
    
    @Override
    public String getStrategyName() {
        return "round-robin";
    }
}

package repositories;

import domain.AuditLog;
import java.time.Instant;
import java.util.List;

/**
 * Repository interface cho AuditLog entity
 */
public interface AuditLogRepository {
    Long write(AuditLog auditLog);
    
    /**
     * Tìm kiếm audit log với phân trang
     */
    List<AuditLog> search(Integer userId, String actionCode, String entityName,
                          Instant fromDate, Instant toDate, 
                          int page, int pageSize, String sortOrder);
    
    long count(Integer userId, String actionCode, String entityName,
               Instant fromDate, Instant toDate);
}

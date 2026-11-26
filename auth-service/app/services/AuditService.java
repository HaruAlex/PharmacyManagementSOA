package services;

import domain.AuditLog;
import domain.User;
import dto.AuditLogResponse;
import dto.PagedResult;
import repositories.AuditLogRepository;
import repositories.UserRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service xử lý Audit Logs
 */
@Singleton
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Inject
    public AuditService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public void write(Integer userId, Integer branchId, String actionCode,
                      String entityName, Long entityId, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setBranchId(branchId);
        auditLog.setActionCode(actionCode);
        auditLog.setEntityName(entityName);
        auditLog.setEntityId(entityId);
        auditLog.setDetails(details != null && details.length() > 1000
                ? details.substring(0, 1000) : details);
        auditLog.setCreatedAt(clock.instant());

        try {
            auditLogRepository.write(auditLog);
        } catch (Exception e) {
            System.err.println("Failed to write audit log: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Tìm kiếm audit logs với filter và phân trang
     */
    public PagedResult<AuditLogResponse> searchAuditLogs(
            Integer userId,
            String actionCode,
            String entityName,
            Instant fromDate,
            Instant toDate,
            int page,
            int pageSize,
            String sortOrder) {
        
        // Kiểm tra kích thước trang
        if (pageSize > 100) {
            pageSize = 100;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        if (page < 1) {
            page = 1;
        }
        
        // Kiểm tra thứ tự sắp xếp
        if (!"ASC".equalsIgnoreCase(sortOrder) && !"DESC".equalsIgnoreCase(sortOrder)) {
            sortOrder = "DESC";
        }
        
        // Search audit logs
        List<AuditLog> auditLogs = auditLogRepository.search(
            userId, actionCode, entityName, fromDate, toDate,
            page, pageSize, sortOrder
        );
        
        // Count total
        long totalItems = auditLogRepository.count(
            userId, actionCode, entityName, fromDate, toDate
        );
        
        // Tạo response kèm username
        List<AuditLogResponse> responses = auditLogs.stream()
            .map(this::buildAuditLogResponse)
            .collect(Collectors.toList());
        
        return new PagedResult<>(responses, page, pageSize, totalItems);
    }

    /**
     * Tạo AuditLogResponse từ AuditLog
     */
    private AuditLogResponse buildAuditLogResponse(AuditLog auditLog) {
        AuditLogResponse response = new AuditLogResponse();
        response.setAuditLogId(auditLog.getAuditLogId());
        response.setUserId(auditLog.getUserId());
        response.setBranchId(auditLog.getBranchId());
        response.setActionCode(auditLog.getActionCode());
        response.setEntityName(auditLog.getEntityName());
        response.setEntityId(auditLog.getEntityId());
        response.setDetails(auditLog.getDetails());
        response.setCreatedAt(auditLog.getCreatedAt());
        
        // Lấy username nếu có
        if (auditLog.getUserId() != null) {
            userRepository.getById(auditLog.getUserId()).ifPresent(user -> {
                response.setUsername(user.getUsername());
            });
        }
        
        return response;
    }
}

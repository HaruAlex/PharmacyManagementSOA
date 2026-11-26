package repositories.jdbc;

import domain.AuditLog;
import play.db.Database;
import repositories.AuditLogRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation của AuditLogRepository
 */
@Singleton
public class JdbcAuditLogRepository implements AuditLogRepository {
    private final Database database;
    private static final int MAX_DETAILS_LENGTH = 1000;

    @Inject
    public JdbcAuditLogRepository(Database database) {
        this.database = database;
    }

    @Override
    public Long write(AuditLog auditLog) {
        String sql = "INSERT INTO auth.AuditLogs (UserId, BranchId, ActionCode, EntityName, EntityId, Details) VALUES (?, ?, ?, ?, ?, ?)";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                if (auditLog.getUserId() != null) {
                    ps.setInt(1, auditLog.getUserId());
                } else {
                    ps.setNull(1, Types.INTEGER);
                }
                
                if (auditLog.getBranchId() != null) {
                    ps.setInt(2, auditLog.getBranchId());
                } else {
                    ps.setNull(2, Types.INTEGER);
                }
                
                ps.setString(3, auditLog.getActionCode());
                ps.setString(4, auditLog.getEntityName());
                
                if (auditLog.getEntityId() != null) {
                    ps.setLong(5, auditLog.getEntityId());
                } else {
                    ps.setNull(5, Types.BIGINT);
                }
                
                // Cắt details nếu quá dài
                String details = auditLog.getDetails();
                if (details != null && details.length() > MAX_DETAILS_LENGTH) {
                    details = details.substring(0, MAX_DETAILS_LENGTH);
                }
                ps.setString(6, details);
                
                ps.executeUpdate();
                
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    throw new SQLException("Failed to get generated AuditLogId");
                }
            }
        });
    }

    @Override
    public List<AuditLog> search(Integer userId, String actionCode, String entityName,
                                  Instant fromDate, Instant toDate, int page, int pageSize, String sortOrder) {
        StringBuilder sql = new StringBuilder("SELECT * FROM auth.AuditLogs WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (userId != null) {
            sql.append(" AND UserId = ?");
            params.add(userId);
        }

        if (actionCode != null && !actionCode.trim().isEmpty()) {
            sql.append(" AND ActionCode = ?");
            params.add(actionCode);
        }

        if (entityName != null && !entityName.trim().isEmpty()) {
            sql.append(" AND EntityName = ?");
            params.add(entityName);
        }

        if (fromDate != null) {
            sql.append(" AND CreatedAt >= ?");
            params.add(Timestamp.from(fromDate));
        }

        if (toDate != null) {
            sql.append(" AND CreatedAt <= ?");
            params.add(Timestamp.from(toDate));
        }

        // Sort order
        String validSortOrder = "DESC".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        sql.append(" ORDER BY CreatedAt ").append(validSortOrder);

        int offset = (page - 1) * pageSize;
        sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(pageSize);

        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof Timestamp) {
                        ps.setTimestamp(i + 1, (Timestamp) param);
                    } else {
                        ps.setObject(i + 1, param);
                    }
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    List<AuditLog> logs = new ArrayList<>();
                    while (rs.next()) {
                        logs.add(mapAuditLog(rs));
                    }
                    return logs;
                }
            }
        });
    }

    @Override
    public long count(Integer userId, String actionCode, String entityName,
                      Instant fromDate, Instant toDate) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM auth.AuditLogs WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (userId != null) {
            sql.append(" AND UserId = ?");
            params.add(userId);
        }

        if (actionCode != null && !actionCode.trim().isEmpty()) {
            sql.append(" AND ActionCode = ?");
            params.add(actionCode);
        }

        if (entityName != null && !entityName.trim().isEmpty()) {
            sql.append(" AND EntityName = ?");
            params.add(entityName);
        }

        if (fromDate != null) {
            sql.append(" AND CreatedAt >= ?");
            params.add(Timestamp.from(fromDate));
        }

        if (toDate != null) {
            sql.append(" AND CreatedAt <= ?");
            params.add(Timestamp.from(toDate));
        }

        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof Timestamp) {
                        ps.setTimestamp(i + 1, (Timestamp) param);
                    } else {
                        ps.setObject(i + 1, param);
                    }
                }
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    return 0L;
                }
            }
        });
    }

    private AuditLog mapAuditLog(ResultSet rs) throws SQLException {
        AuditLog log = new AuditLog();
        log.setAuditLogId(rs.getLong("AuditLogId"));
        
        int userId = rs.getInt("UserId");
        if (!rs.wasNull()) {
            log.setUserId(userId);
        }
        
        int branchId = rs.getInt("BranchId");
        if (!rs.wasNull()) {
            log.setBranchId(branchId);
        }
        
        log.setActionCode(rs.getString("ActionCode"));
        log.setEntityName(rs.getString("EntityName"));
        
        long entityId = rs.getLong("EntityId");
        if (!rs.wasNull()) {
            log.setEntityId(entityId);
        }
        
        log.setDetails(rs.getString("Details"));
        
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) {
            log.setCreatedAt(createdAt.toInstant());
        }
        
        return log;
    }
}

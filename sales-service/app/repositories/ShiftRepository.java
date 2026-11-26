package repositories;

import models.Shift;
import models.Shift.ShiftStatus;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import play.db.Database;

/**
 * ShiftRepository - JDBC Data Access Layer cho Shift (Ca làm việc)
 * 
 * Database Table: sales.Shifts
 * - ShiftId BIGINT PK
 * - BranchId INT (FK → catalog.Branches)
 * - UserId INT (FK → auth.Users)
 * - OpenedAt DATETIME2 (thời gian mở ca)
 * - ClosedAt DATETIME2 (thời gian chốt ca, nullable)
 * - OpeningCash DECIMAL(18,2) (tiền mặt lúc mở)
 * - ActualClosingCash DECIMAL(18,2) (tiền mặt thực tế, nullable)
 * - Status VARCHAR(15) ('OPEN' | 'CLOSED')
 * - UNIQUE INDEX: (UserId, BranchId) WHERE Status = 'OPEN'
 * 
 * Quy tắc:
 * - Mỗi user chỉ có 1 ca mở ở 1 chi nhánh (enforced by UNIQUE INDEX)
 * - Chỉ ca OPEN mới có thể chốt được
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class ShiftRepository {
    
    private final Database db;
    
    @Inject
    public ShiftRepository(Database db) {
        this.db = db;
    }
    
    /**
     * Lấy shift theo ID
     */
    public Optional<Shift> findById(Long shiftId) {
        return db.withConnection(conn -> {
            String sql = "SELECT ShiftId, BranchId, UserId, OpenedAt, ClosedAt, " +
                         "OpeningCash, ActualClosingCash, Status FROM sales.Shifts WHERE ShiftId = ?";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, shiftId);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToShift(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }
    
    /**
     * Lấy shift đang mở của user tại chi nhánh
     * Chỉ 1 ca có thể mở tại 1 lúc (enforced by UNIQUE INDEX)
     */
    public Optional<Shift> findOpenShiftByUserAndBranch(Long userId, Long branchId) {
        return db.withConnection(conn -> {
            String sql = "SELECT ShiftId, BranchId, UserId, OpenedAt, ClosedAt, " +
                         "OpeningCash, ActualClosingCash, Status FROM sales.Shifts " +
                         "WHERE UserId = ? AND BranchId = ? AND Status = 'OPEN'";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                stmt.setLong(2, branchId);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToShift(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }
    
    /**
     * Lấy tất cả shifts của một user (phân trang)
     * Sắp xếp theo OpenedAt DESC (mới nhất trước)
     */
    public List<Shift> findByUserId(Long userId, int page, int pageSize) {
        return db.withConnection(conn -> {
            int offset = (page - 1) * pageSize;
            String sql = "SELECT ShiftId, BranchId, UserId, OpenedAt, ClosedAt, " +
                         "OpeningCash, ActualClosingCash, Status FROM sales.Shifts " +
                         "WHERE UserId = ? ORDER BY OpenedAt DESC " +
                         "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            
            List<Shift> shifts = new java.util.ArrayList<>();
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, userId);
                stmt.setInt(2, offset);
                stmt.setInt(3, pageSize);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        shifts.add(mapRowToShift(rs));
                    }
                }
            }
            return shifts;
        });
    }
    
    /**
     * Lấy tất cả shifts của một chi nhánh theo ngày
     * Dùng cho báo cáo
     */
    public List<Shift> findByBranchAndDate(Long branchId, LocalDate date) {
        return db.withConnection(conn -> {
            String sql = "SELECT ShiftId, BranchId, UserId, OpenedAt, ClosedAt, " +
                         "OpeningCash, ActualClosingCash, Status FROM sales.Shifts " +
                         "WHERE BranchId = ? AND CAST(OpenedAt AS DATE) = ? " +
                         "ORDER BY OpenedAt";
            
            List<Shift> shifts = new java.util.ArrayList<>();
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, branchId);
                stmt.setDate(2, java.sql.Date.valueOf(date));
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        shifts.add(mapRowToShift(rs));
                    }
                }
            }
            return shifts;
        });
    }
    
    /**
     * Tạo shift mới (mở ca)
     * INSERT và trả về ShiftId vừa tạo
     */
    public Long create(Shift shift) {
        return db.withConnection(conn -> {
            String sql = "INSERT INTO sales.Shifts (BranchId, UserId, OpenedAt, " +
                         "OpeningCash, Status) VALUES (?, ?, ?, ?, ?); " +
                         "SELECT CAST(SCOPE_IDENTITY() AS BIGINT)";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, shift.getBranchId());
                stmt.setLong(2, shift.getUserId());
                stmt.setObject(3, shift.getOpenedAt());
                stmt.setBigDecimal(4, shift.getOpeningCash());
                stmt.setString(5, shift.getStatus().name());
                
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Long shiftId = rs.getLong(1);
                        shift.setShiftId(shiftId);
                        return shiftId;
                    }
                }
            }
            throw new RuntimeException("Failed to create Shift - no generated ID");
        });
    }
    
    /**
     * Cập nhật shift (chốt ca)
     * UPDATE: ClosedAt, ActualClosingCash, Status
     */
    public boolean update(Shift shift) {
        return db.withConnection(conn -> {
            String sql = "UPDATE sales.Shifts SET ClosedAt = ?, " +
                         "ActualClosingCash = ?, Status = ? WHERE ShiftId = ?";
            
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, shift.getClosedAt());
                stmt.setBigDecimal(2, shift.getActualClosingCash());
                stmt.setString(3, shift.getStatus().name());
                stmt.setLong(4, shift.getShiftId());
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        });
    }
    
    /**
     * Kiểm tra user có ca đang mở ở chi nhánh không
     */
    public boolean hasOpenShift(Long userId, Long branchId) {
        return findOpenShiftByUserAndBranch(userId, branchId).isPresent();
    }
    
    /**
     * Lấy tất cả shifts đang mở của một chi nhánh
     * Dùng cho quản lý chi nhánh xem ai đang bán
     */
    public List<Shift> findAllOpenShiftsByBranch(Long branchId) {
        return db.withConnection(conn -> {
            String sql = "SELECT ShiftId, BranchId, UserId, OpenedAt, ClosedAt, " +
                         "OpeningCash, ActualClosingCash, Status FROM sales.Shifts " +
                         "WHERE BranchId = ? AND Status = 'OPEN' " +
                         "ORDER BY OpenedAt";
            
            List<Shift> shifts = new java.util.ArrayList<>();
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, branchId);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        shifts.add(mapRowToShift(rs));
                    }
                }
            }
            return shifts;
        });
    }
    
    /**
     * Map ResultSet row to Shift object
     */
    private Shift mapRowToShift(java.sql.ResultSet rs) throws java.sql.SQLException {
        Shift shift = new Shift();
        shift.setShiftId(rs.getLong("ShiftId"));
        shift.setBranchId(rs.getLong("BranchId"));
        shift.setUserId(rs.getLong("UserId"));
        shift.setOpenedAt(rs.getObject("OpenedAt", LocalDateTime.class));
        
        java.sql.Timestamp closedAt = rs.getTimestamp("ClosedAt");
        if (closedAt != null) {
            shift.setClosedAt(closedAt.toLocalDateTime());
        }
        
        shift.setOpeningCash(rs.getBigDecimal("OpeningCash"));
        
        java.math.BigDecimal actualClosingCash = rs.getBigDecimal("ActualClosingCash");
        if (!rs.wasNull()) {
            shift.setActualClosingCash(actualClosingCash);
        }
        
        shift.setStatus(ShiftStatus.valueOf(rs.getString("Status")));
        return shift;
    }
}

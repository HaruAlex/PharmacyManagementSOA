package models;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Shift Model - Ca làm việc
 * 
 * Database Table: sales.Shifts
 * Mối quan hệ:
 * - FK UserId → auth.Users (nhân viên bán hàng)
 * - FK BranchId → catalog.Branches
 * - 1:N Invoices (các hóa đơn được lập trong ca)
 * 
 * Mỗi nhân viên chỉ có thể mở 1 ca tại 1 chi nhánh
 * (UNIQUE INDEX UX_Shifts_OneOpenPerUserBranch)
 * 
 * Mở ca: Nhân viên nhập tiền mặt lúc bắt đầu
 * Chốt ca: Nhân viên nhập tiền mặt thực tế, so sánh với tính toán
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class Shift {
    
    public enum ShiftStatus {
        OPEN,   // Ca đang mở
        CLOSED  // Ca đã chốt
    }
    
    private Long shiftId;              // Primary Key
    private Long branchId;             // FK -> catalog.Branches
    private Long userId;               // FK -> auth.Users
    
    // Thời gian
    private LocalDateTime openedAt;    // Thời gian mở ca
    private LocalDateTime closedAt;    // Thời gian chốt ca (nullable)
    
    // Tiền
    private BigDecimal openingCash;    // Tiền mặt lúc mở ca
    private BigDecimal actualClosingCash; // Tiền mặt thực tế lúc chốt ca (nullable)
    
    // Trạng thái
    private ShiftStatus status;        // OPEN hoặc CLOSED
    
    // Constructors
    public Shift() {
    }
    
    public Shift(Long branchId, Long userId, BigDecimal openingCash) {
        this.branchId = branchId;
        this.userId = userId;
        this.openingCash = openingCash;
        this.openedAt = LocalDateTime.now();
        this.status = ShiftStatus.OPEN;
    }
    
    // Getters & Setters
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    
    public BigDecimal getOpeningCash() { return openingCash; }
    public void setOpeningCash(BigDecimal openingCash) { this.openingCash = openingCash; }
    
    public BigDecimal getActualClosingCash() { return actualClosingCash; }
    public void setActualClosingCash(BigDecimal actualClosingCash) { this.actualClosingCash = actualClosingCash; }
    
    public ShiftStatus getStatus() { return status; }
    public void setStatus(ShiftStatus status) { this.status = status; }
    
    // Business Methods
    /**
     * Tính chênh lệch tiền = actualClosingCash - openingCash
     * Nếu > 0: thừa tiền, < 0: thiếu tiền
     */
    public BigDecimal calculateCashDifference() {
        if (actualClosingCash != null) {
            return actualClosingCash.subtract(openingCash);
        }
        return null;
    }
    
    /**
     * Kiểm tra ca có thể chốt được không (phải mở trước)
     */
    public boolean canClose() {
        return this.status == ShiftStatus.OPEN;
    }
    
    @Override
    public String toString() {
        return "Shift{" +
                "shiftId=" + shiftId +
                ", branchId=" + branchId +
                ", userId=" + userId +
                ", status=" + status +
                ", openedAt=" + openedAt +
                ", closedAt=" + closedAt +
                '}';
    }
}

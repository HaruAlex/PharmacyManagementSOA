package services;

import models.Shift;
import models.Shift.ShiftStatus;
import repositories.ShiftRepository;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * ShiftService - Xử lý business logic cho Shift (Ca làm việc)
 * 
 * Spec: UC-BH-08 "Mở ca, chốt ca"
 * - FR-BH-12: Mở ca, chốt ca: tổng hợp tiền theo ca, đối chiếu tiền mặt thực tế, ghi chênh lệch
 * 
 * Tính năng:
 * - Mở ca: User nhập tiền mặt lúc bắt đầu (OpeningCash)
 * - Chốt ca: User nhập tiền mặt thực tế (ActualClosingCash), tính chênh lệch
 * - Kiểm tra: Chỉ 1 ca mở tại 1 lúc (enforced by UNIQUE INDEX)
 * 
 * API Endpoint:
 * - POST /sales/shifts/open {userId, branchId, openingCash}
 * - POST /sales/shifts/{id}/close {actualClosingCash}
 * - GET /sales/shifts/current (ca đang mở)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
@Singleton
public class ShiftService {
    
    private final ShiftRepository shiftRepository;
    
    @Inject
    public ShiftService(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }
    
    /**
     * Mở ca mới (FR-BH-12)
     * 
     * Quy trình:
     * 1. Validate dữ liệu đầu vào
     * 2. Kiểm tra user không có ca nào đang mở ở chi nhánh này
     * 3. Tạo ca mới với trạng thái OPEN
     * 4. Trả shift đã tạo
     * 
     * @param userId ID nhân viên (FK auth.Users)
     * @param branchId ID chi nhánh (FK catalog.Branches)
     * @param openingCash Tiền mặt lúc mở ca (>= 0)
     * @return Shift vừa tạo
     * @throws IllegalArgumentException nếu dữ liệu không hợp lệ
     * @throws IllegalStateException nếu user đã có ca mở
     */
    public Shift openShift(Long userId, Long branchId, BigDecimal openingCash) {
        // Validate input
        if (userId == null || branchId == null) {
            throw new IllegalArgumentException("UserId và BranchId không được null");
        }
        if (openingCash == null || openingCash.signum() < 0) {
            throw new IllegalArgumentException("Opening cash phải >= 0");
        }
        
        // Kiểm tra user đã có ca mở ở chi nhánh này chưa
        // (enforced by UNIQUE INDEX UX_Shifts_OneOpenPerUserBranch)
        if (shiftRepository.hasOpenShift(userId, branchId)) {
            throw new IllegalStateException(
                "User ID " + userId + " đã có 1 ca đang mở tại chi nhánh " + branchId
            );
        }
        
        // Tạo shift mới
        Shift newShift = new Shift(branchId, userId, openingCash);
        newShift.setOpenedAt(LocalDateTime.now());
        newShift.setStatus(ShiftStatus.OPEN);
        
        shiftRepository.create(newShift);
        return newShift;
    }
    
    /**
     * Chốt ca (FR-BH-12)
     * 
     * Quy trình:
     * 1. Validate dữ liệu đầu vào
     * 2. Lấy shift theo ID
     * 3. Kiểm tra shift ở trạng thái OPEN
     * 4. Cập nhật: ClosedAt, ActualClosingCash, Status = CLOSED
     * 5. Trả shift sau khi chốt (gồm chênh lệch tiền)
     * 
     * @param shiftId ID ca cần chốt
     * @param actualClosingCash Tiền mặt thực tế (>= 0)
     * @return Shift sau khi chốt (có thể lấy calculateCashDifference())
     * @throws IllegalArgumentException nếu dữ liệu không hợp lệ hoặc shift không tồn tại
     * @throws IllegalStateException nếu shift không ở trạng thái OPEN
     */
    public Shift closeShift(Long shiftId, BigDecimal actualClosingCash) {
        if (shiftId == null) {
            throw new IllegalArgumentException("ShiftId không được null");
        }
        if (actualClosingCash == null || actualClosingCash.signum() < 0) {
            throw new IllegalArgumentException("Actual closing cash phải >= 0");
        }
        
        // Lấy shift hiện tại
        Shift shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new IllegalArgumentException("Shift ID " + shiftId + " không tìm thấy"));
        
        // Kiểm tra trạng thái
        if (shift.getStatus() != ShiftStatus.OPEN) {
            throw new IllegalStateException("Shift ID " + shiftId + " không ở trạng thái OPEN, hiện tại là " + shift.getStatus());
        }
        
        // Cập nhật dữ liệu chốt ca
        shift.setClosedAt(LocalDateTime.now());
        shift.setActualClosingCash(actualClosingCash);
        shift.setStatus(ShiftStatus.CLOSED);
        
        // Lưu vào database
        boolean updated = shiftRepository.update(shift);
        if (!updated) {
            throw new RuntimeException("Cập nhật shift ID " + shiftId + " thất bại");
        }
        
        return shift;
    }
    
    /**
     * Lấy shift theo ID
     */
    public Optional<Shift> getShiftById(Long shiftId) {
        if (shiftId == null) {
            throw new IllegalArgumentException("ShiftId không được null");
        }
        return shiftRepository.findById(shiftId);
    }
    
    /**
     * Lấy ca đang mở của user tại chi nhánh
     * GET /sales/shifts/current
     */
    public Optional<Shift> getCurrentShift(Long userId, Long branchId) {
        if (userId == null || branchId == null) {
            throw new IllegalArgumentException("UserId và BranchId không được null");
        }
        return shiftRepository.findOpenShiftByUserAndBranch(userId, branchId);
    }
    
    /**
     * Lấy lịch sử shifts của user (phân trang)
     */
    public List<Shift> getUserShiftHistory(Long userId, int page, int pageSize) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId không được null");
        }
        if (page < 1 || pageSize < 1) {
            throw new IllegalArgumentException("Page và pageSize phải >= 1");
        }
        
        return shiftRepository.findByUserId(userId, page, pageSize);
    }
    
    /**
     * Lấy tất cả shifts đang mở của chi nhánh
     * Dùng cho quản lý chi nhánh xem ai đang bán
     */
    public List<Shift> getOpenShiftsByBranch(Long branchId) {
        if (branchId == null) {
            throw new IllegalArgumentException("BranchId không được null");
        }
        return shiftRepository.findAllOpenShiftsByBranch(branchId);
    }
    
    /**
     * Lấy tất cả shifts của chi nhánh theo ngày
     * Dùng cho báo cáo doanh thu theo ca
     */
    public List<Shift> getShiftsByBranchAndDate(Long branchId, LocalDate date) {
        if (branchId == null || date == null) {
            throw new IllegalArgumentException("BranchId và date không được null");
        }
        return shiftRepository.findByBranchAndDate(branchId, date);
    }
    
    /**
     * Tính chênh lệch tiền sau chốt ca
     * Chênh lệch = ActualClosingCash - OpeningCash
     * - > 0: thừa tiền
     * - < 0: thiếu tiền
     * - = 0: đúng
     */
    public BigDecimal calculateCashDifference(Long shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new IllegalArgumentException("Shift ID " + shiftId + " không tìm thấy"));
        
        return shift.calculateCashDifference();
    }
    
    /**
     * Kiểm tra ca có thể chốt được không (phải ở trạng thái OPEN)
     */
    public boolean canCloseShift(Long shiftId) {
        Shift shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new IllegalArgumentException("Shift ID " + shiftId + " không tìm thấy"));
        
        return shift.canClose();
    }
    
    /**
     * Kiểm tra user có ca nào đang mở ở chi nhánh không
     */
    public boolean userHasOpenShift(Long userId, Long branchId) {
        if (userId == null || branchId == null) {
            throw new IllegalArgumentException("UserId và BranchId không được null");
        }
        return shiftRepository.hasOpenShift(userId, branchId);
    }
}

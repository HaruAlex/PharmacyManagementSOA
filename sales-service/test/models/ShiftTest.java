package models;

import org.junit.Test;
import static org.junit.Assert.*;
import java.math.BigDecimal;

/**
 * Unit Tests cho Shift Model
 * 
 * Test cases:
 * 1. Tạo Shift mới - kiểm tra initialization
 * 2. Tính chênh lệch tiền - verify calculateCashDifference()
 * 3. Kiểm tra có thể chốt ca - verify canClose()
 * 4. Status transitions
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class ShiftTest {
    
    /**
     * TC001: Tạo Shift mới
     * Verify: Các field được initialize đúng
     */
    @Test
    public void testShiftCreation() {
        // Arrange & Act
        Shift shift = new Shift(1L, 2L, new BigDecimal("500000"));
        
        // Assert
        assertEquals(Long.valueOf(1L), shift.getBranchId());
        assertEquals(Long.valueOf(2L), shift.getUserId());
        assertEquals(new BigDecimal("500000"), shift.getOpeningCash());
        assertEquals(Shift.ShiftStatus.OPEN, shift.getStatus());
        assertNotNull(shift.getOpenedAt());
        assertNull(shift.getClosedAt());
        assertNull(shift.getActualClosingCash());
    }
    
    /**
     * TC002: Tính chênh lệch tiền - thừa tiền
     * Verify: difference = actualClosingCash - openingCash (> 0)
     */
    @Test
    public void testCalculateCashDifferencePositive() {
        // Arrange
        Shift shift = new Shift(1L, 2L, new BigDecimal("500000"));
        shift.setActualClosingCash(new BigDecimal("520000"));
        
        // Act
        BigDecimal difference = shift.calculateCashDifference();
        
        // Assert
        assertEquals(new BigDecimal("20000"), difference); // thừa 20k
    }
    
    /**
     * TC003: Tính chênh lệch tiền - thiếu tiền
     * Verify: difference = actualClosingCash - openingCash (< 0)
     */
    @Test
    public void testCalculateCashDifferenceNegative() {
        // Arrange
        Shift shift = new Shift(1L, 2L, new BigDecimal("500000"));
        shift.setActualClosingCash(new BigDecimal("480000"));
        
        // Act
        BigDecimal difference = shift.calculateCashDifference();
        
        // Assert
        assertEquals(new BigDecimal("-20000"), difference); // thiếu 20k
    }
    
    /**
     * TC004: Tính chênh lệch tiền - bằng nhau
     * Verify: difference = 0 khi khớp
     */
    @Test
    public void testCalculateCashDifferenceEqual() {
        // Arrange
        Shift shift = new Shift(1L, 2L, new BigDecimal("500000"));
        shift.setActualClosingCash(new BigDecimal("500000"));
        
        // Act
        BigDecimal difference = shift.calculateCashDifference();
        
        // Assert
        assertEquals(BigDecimal.ZERO, difference);
    }
    
    /**
     * TC005: Tính chênh lệch tiền - khi chưa chốt ca
     * Verify: Return null khi actualClosingCash là null
     */
    @Test
    public void testCalculateCashDifferenceNull() {
        // Arrange
        Shift shift = new Shift(1L, 2L, new BigDecimal("500000"));
        // actualClosingCash = null
        
        // Act
        BigDecimal difference = shift.calculateCashDifference();
        
        // Assert
        assertNull(difference);
    }
    
    /**
     * TC006: Kiểm tra có thể chốt ca - status OPEN
     * Verify: canClose() return true khi status = OPEN
     */
    @Test
    public void testCanCloseWhenOpen() {
        // Arrange
        Shift shift = new Shift(1L, 2L, new BigDecimal("500000"));
        
        // Act & Assert
        assertTrue(shift.canClose()); // ca OPEN có thể chốt
    }
    
    /**
     * TC007: Kiểm tra có thể chốt ca - status CLOSED
     * Verify: canClose() return false khi status = CLOSED
     */
    @Test
    public void testCanCloseWhenClosed() {
        // Arrange
        Shift shift = new Shift(1L, 2L, new BigDecimal("500000"));
        shift.setStatus(Shift.ShiftStatus.CLOSED);
        
        // Act & Assert
        assertFalse(shift.canClose()); // ca CLOSED không thể chốt lại
    }
    
    /**
     * TC008: Set status OPEN → CLOSED
     * Verify: Status thay đổi đúng
     */
    @Test
    public void testShiftStatusTransition() {
        // Arrange
        Shift shift = new Shift(1L, 2L, new BigDecimal("500000"));
        
        // Act
        shift.setStatus(Shift.ShiftStatus.CLOSED);
        
        // Assert
        assertEquals(Shift.ShiftStatus.CLOSED, shift.getStatus());
    }
    
    /**
     * TC009: Set closedAt khi chốt ca
     * Verify: closedAt được set khi chốt
     */
    @Test
    public void testShiftClosedAt() {
        // Arrange
        Shift shift = new Shift(1L, 2L, new BigDecimal("500000"));
        
        // Act
        shift.setClosedAt(java.time.LocalDateTime.now());
        shift.setStatus(Shift.ShiftStatus.CLOSED);
        
        // Assert
        assertNotNull(shift.getClosedAt());
        assertEquals(Shift.ShiftStatus.CLOSED, shift.getStatus());
    }
    
    /**
     * TC010: toString() method
     * Verify: toString() không throw exception
     */
    @Test
    public void testShiftToString() {
        // Arrange
        Shift shift = new Shift(1L, 2L, new BigDecimal("500000"));
        shift.setShiftId(10L);
        
        // Act
        String str = shift.toString();
        
        // Assert
        assertNotNull(str);
        assertTrue(str.contains("Shift"));
        assertTrue(str.contains("OPEN"));
    }
}

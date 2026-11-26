package models;

import org.junit.Test;
import static org.junit.Assert.*;
import java.math.BigDecimal;

/**
 * Unit Tests cho InvoiceDetail Model
 * 
 * Test cases:
 * 1. Tạo InvoiceDetail mới
 * 2. Tính toán lineTotal
 * 3. Conversion giữa quantity và quantityBase
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class InvoiceDetailTest {
    
    /**
     * TC001: Tạo InvoiceDetail mới
     * Verify: Các field được initialize đúng
     */
    @Test
    public void testInvoiceDetailCreation() {
        // Arrange & Act
        InvoiceDetail detail = new InvoiceDetail(
            1L, 10L, 2L,
            new BigDecimal("2"), new BigDecimal("20")
        );
        
        // Assert
        assertEquals(Long.valueOf(1L), detail.getInvoiceId());
        assertEquals(Long.valueOf(10L), detail.getDrugId());
        assertEquals(Long.valueOf(2L), detail.getUnitId());
        assertEquals(new BigDecimal("2"), detail.getQuantity());
        assertEquals(new BigDecimal("20"), detail.getQuantityBase());
        assertEquals(BigDecimal.ZERO, detail.getDiscountAmount());
    }
    
    /**
     * TC002: Tính toán lineTotal - không có discount
     * Verify: lineTotal = quantity * unitPrice
     */
    @Test
    public void testCalculateLineTotalNoDiscount() {
        // Arrange
        InvoiceDetail detail = new InvoiceDetail(
            1L, 10L, 2L,
            new BigDecimal("2"), new BigDecimal("20")
        );
        detail.setUnitPrice(new BigDecimal("600000")); // 600k per unit
        detail.setDiscountAmount(BigDecimal.ZERO);
        
        // Act
        detail.calculateLineTotal();
        
        // Assert
        // lineTotal = 2 * 600000 = 1200000
        assertEquals(new BigDecimal("1200000"), detail.getLineTotal());
    }
    
    /**
     * TC003: Tính toán lineTotal - có discount
     * Verify: lineTotal = (quantity * unitPrice) - discountAmount
     */
    @Test
    public void testCalculateLineTotalWithDiscount() {
        // Arrange
        InvoiceDetail detail = new InvoiceDetail(
            1L, 10L, 2L,
            new BigDecimal("2"), new BigDecimal("20")
        );
        detail.setUnitPrice(new BigDecimal("600000"));
        detail.setDiscountAmount(new BigDecimal("100000"));
        
        // Act
        detail.calculateLineTotal();
        
        // Assert
        // lineTotal = (2 * 600000) - 100000 = 1200000 - 100000 = 1100000
        assertEquals(new BigDecimal("1100000"), detail.getLineTotal());
    }
    
    /**
     * TC004: Tính toán lineTotal - discount lớn hơn subtotal
     * Verify: lineTotal không âm = 0
     */
    @Test
    public void testCalculateLineTotalDiscountTooLarge() {
        // Arrange
        InvoiceDetail detail = new InvoiceDetail(
            1L, 10L, 2L,
            new BigDecimal("1"), new BigDecimal("10")
        );
        detail.setUnitPrice(new BigDecimal("100000"));
        detail.setDiscountAmount(new BigDecimal("200000")); // lớn hơn subtotal
        
        // Act
        detail.calculateLineTotal();
        
        // Assert
        assertEquals(BigDecimal.ZERO, detail.getLineTotal());
    }
    
    /**
     * TC005: Tính toán lineTotal - unitPrice = 0
     * Verify: lineTotal = 0
     */
    @Test
    public void testCalculateLineTotalZeroPrice() {
        // Arrange
        InvoiceDetail detail = new InvoiceDetail(
            1L, 10L, 2L,
            new BigDecimal("2"), new BigDecimal("20")
        );
        detail.setUnitPrice(BigDecimal.ZERO);
        detail.setDiscountAmount(BigDecimal.ZERO);
        
        // Act
        detail.calculateLineTotal();
        
        // Assert
        assertEquals(BigDecimal.ZERO, detail.getLineTotal());
    }
    
    /**
     * TC006: Set unitPrice
     * Verify: unitPrice được set đúng
     */
    @Test
    public void testSetUnitPrice() {
        // Arrange & Act
        InvoiceDetail detail = new InvoiceDetail(
            1L, 10L, 2L,
            new BigDecimal("2"), new BigDecimal("20")
        );
        detail.setUnitPrice(new BigDecimal("1200000"));
        
        // Assert
        assertEquals(new BigDecimal("1200000"), detail.getUnitPrice());
    }
    
    /**
     * TC007: Set discountAmount
     * Verify: discountAmount được set đúng
     */
    @Test
    public void testSetDiscountAmount() {
        // Arrange & Act
        InvoiceDetail detail = new InvoiceDetail(
            1L, 10L, 2L,
            new BigDecimal("2"), new BigDecimal("20")
        );
        detail.setDiscountAmount(new BigDecimal("50000"));
        
        // Assert
        assertEquals(new BigDecimal("50000"), detail.getDiscountAmount());
    }
    
    /**
     * TC008: Set quantity và quantityBase
     * Verify: Các field được set đúng
     */
    @Test
    public void testSetQuantities() {
        // Arrange
        InvoiceDetail detail = new InvoiceDetail(
            1L, 10L, 2L,
            new BigDecimal("1"), new BigDecimal("10")
        );
        
        // Act
        detail.setQuantity(new BigDecimal("3"));
        detail.setQuantityBase(new BigDecimal("30"));
        
        // Assert
        assertEquals(new BigDecimal("3"), detail.getQuantity());
        assertEquals(new BigDecimal("30"), detail.getQuantityBase());
    }
    
    /**
     * TC009: toString() method
     * Verify: toString() không throw exception
     */
    @Test
    public void testInvoiceDetailToString() {
        // Arrange
        InvoiceDetail detail = new InvoiceDetail(
            1L, 10L, 2L,
            new BigDecimal("2"), new BigDecimal("20")
        );
        detail.setInvoiceDetailId(50L);
        detail.setUnitPrice(new BigDecimal("600000"));
        detail.calculateLineTotal();
        
        // Act
        String str = detail.toString();
        
        // Assert
        assertNotNull(str);
        assertTrue(str.contains("InvoiceDetail"));
        assertTrue(str.contains("drugId=10"));
    }
}

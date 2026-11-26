package models;

import org.junit.Test;
import static org.junit.Assert.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Unit Tests cho Invoice Model
 * 
 * Test cases:
 * 1. Tạo Invoice mới - kiểm tra initialization
 * 2. Tính toán totalAmount - kiểm tra calculateTotal()
 * 3. Transition status - kiểm tra thay đổi trạng thái
 * 4. Payment method - kiểm tra loại thanh toán
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class InvoiceTest {
    
    /**
     * TC001: Tạo Invoice mới
     * Verify: Các field được initialize đúng
     */
    @Test
    public void testInvoiceCreation() {
        // Arrange
        Long branchId = 1L;
        Long userId = 2L;
        String invoiceCode = "CN001-20261126-001";
        
        // Act
        Invoice invoice = new Invoice(branchId, userId, invoiceCode);
        
        // Assert
        assertEquals(branchId, invoice.getBranchId());
        assertEquals(userId, invoice.getUserId());
        assertEquals(invoiceCode, invoice.getInvoiceCode());
        assertEquals(Invoice.InvoiceStatus.DRAFT, invoice.getStatus());
        assertEquals(BigDecimal.ZERO, invoice.getSubtotal());
        assertEquals(BigDecimal.ZERO, invoice.getDiscountAmount());
        assertEquals(BigDecimal.ZERO, invoice.getTotalAmount());
        assertNotNull(invoice.getInvoiceDate());
    }
    
    /**
     * TC002: Tính toán totalAmount
     * Verify: totalAmount = subtotal - discountAmount
     */
    @Test
    public void testInvoiceCalculateTotal() {
        // Arrange
        Invoice invoice = new Invoice(1L, 2L, "CN001-20261126-001");
        invoice.setSubtotal(new BigDecimal("1000000"));
        invoice.setDiscountAmount(new BigDecimal("100000"));
        
        // Act
        invoice.calculateTotal();
        
        // Assert
        assertEquals(new BigDecimal("900000"), invoice.getTotalAmount());
    }
    
    /**
     * TC003: Tính toán totalAmount với discount = 0
     * Verify: totalAmount = subtotal khi discount = 0
     */
    @Test
    public void testInvoiceCalculateTotalNoDiscount() {
        // Arrange
        Invoice invoice = new Invoice(1L, 2L, "CN001-20261126-001");
        invoice.setSubtotal(new BigDecimal("1000000"));
        invoice.setDiscountAmount(BigDecimal.ZERO);
        
        // Act
        invoice.calculateTotal();
        
        // Assert
        assertEquals(new BigDecimal("1000000"), invoice.getTotalAmount());
    }
    
    /**
     * TC004: Tính toán totalAmount âm (discount > subtotal)
     * Verify: totalAmount không âm, bằng 0
     */
    @Test
    public void testInvoiceCalculateTotalNegative() {
        // Arrange
        Invoice invoice = new Invoice(1L, 2L, "CN001-20261126-001");
        invoice.setSubtotal(new BigDecimal("100000"));
        invoice.setDiscountAmount(new BigDecimal("200000")); // lớn hơn subtotal
        
        // Act
        invoice.calculateTotal();
        
        // Assert
        assertEquals(BigDecimal.ZERO, invoice.getTotalAmount());
    }
    
    /**
     * TC005: Transition status DRAFT → PROCESSING
     * Verify: Status thay đổi đúng
     */
    @Test
    public void testInvoiceStatusTransitionDraftToProcessing() {
        // Arrange
        Invoice invoice = new Invoice(1L, 2L, "CN001-20261126-001");
        
        // Act
        invoice.setStatus(Invoice.InvoiceStatus.PROCESSING);
        
        // Assert
        assertEquals(Invoice.InvoiceStatus.PROCESSING, invoice.getStatus());
    }
    
    /**
     * TC006: Transition status PROCESSING → COMPLETED
     * Verify: Status thay đổi đúng
     */
    @Test
    public void testInvoiceStatusTransitionToCompleted() {
        // Arrange
        Invoice invoice = new Invoice(1L, 2L, "CN001-20261126-001");
        invoice.setStatus(Invoice.InvoiceStatus.PROCESSING);
        
        // Act
        invoice.setStatus(Invoice.InvoiceStatus.COMPLETED);
        
        // Assert
        assertEquals(Invoice.InvoiceStatus.COMPLETED, invoice.getStatus());
    }
    
    /**
     * TC007: Transition status to CANCELLED
     * Verify: Status thay đổi thành CANCELLED
     */
    @Test
    public void testInvoiceStatusTransitionToCancelled() {
        // Arrange
        Invoice invoice = new Invoice(1L, 2L, "CN001-20261126-001");
        
        // Act
        invoice.setStatus(Invoice.InvoiceStatus.CANCELLED);
        
        // Assert
        assertEquals(Invoice.InvoiceStatus.CANCELLED, invoice.getStatus());
    }
    
    /**
     * TC008: Set payment method CASH
     * Verify: Payment method được set đúng
     */
    @Test
    public void testInvoicePaymentMethodCash() {
        // Arrange & Act
        Invoice invoice = new Invoice(1L, 2L, "CN001-20261126-001");
        invoice.setPaymentMethod(Invoice.PaymentMethod.CASH);
        
        // Assert
        assertEquals(Invoice.PaymentMethod.CASH, invoice.getPaymentMethod());
    }
    
    /**
     * TC009: Set payment method BANK_TRANSFER
     * Verify: Payment method được set đúng
     */
    @Test
    public void testInvoicePaymentMethodBankTransfer() {
        // Arrange & Act
        Invoice invoice = new Invoice(1L, 2L, "CN001-20261126-001");
        invoice.setPaymentMethod(Invoice.PaymentMethod.BANK_TRANSFER);
        
        // Assert
        assertEquals(Invoice.PaymentMethod.BANK_TRANSFER, invoice.getPaymentMethod());
    }
    
    /**
     * TC010: Set payment method CARD
     * Verify: Payment method được set đúng
     */
    @Test
    public void testInvoicePaymentMethodCard() {
        // Arrange & Act
        Invoice invoice = new Invoice(1L, 2L, "CN001-20261126-001");
        invoice.setPaymentMethod(Invoice.PaymentMethod.CARD);
        
        // Assert
        assertEquals(Invoice.PaymentMethod.CARD, invoice.getPaymentMethod());
    }
    
    /**
     * TC011: Set nullable fields
     * Verify: Các field nullable được set đúng
     */
    @Test
    public void testInvoiceNullableFields() {
        // Arrange & Act
        Invoice invoice = new Invoice(1L, 2L, "CN001-20261126-001");
        invoice.setCustomerId(100L);
        invoice.setShiftId(50L);
        invoice.setPrescriptionId(10L);
        invoice.setNote("Ghi chú test");
        
        // Assert
        assertEquals(Long.valueOf(100L), invoice.getCustomerId());
        assertEquals(Long.valueOf(50L), invoice.getShiftId());
        assertEquals(Long.valueOf(10L), invoice.getPrescriptionId());
        assertEquals("Ghi chú test", invoice.getNote());
    }
    
    /**
     * TC012: toString() method
     * Verify: toString() không throw exception
     */
    @Test
    public void testInvoiceToString() {
        // Arrange
        Invoice invoice = new Invoice(1L, 2L, "CN001-20261126-001");
        invoice.setInvoiceId(1L);
        invoice.setStatus(Invoice.InvoiceStatus.DRAFT);
        invoice.setTotalAmount(new BigDecimal("500000"));
        
        // Act
        String str = invoice.toString();
        
        // Assert
        assertNotNull(str);
        assertTrue(str.contains("Invoice"));
        assertTrue(str.contains("CN001-20261126-001"));
    }
}

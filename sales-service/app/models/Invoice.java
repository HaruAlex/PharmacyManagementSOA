package models;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Invoice Model - Hóa đơn bán lẻ
 * 
 * Database Table: sales.Invoices
 * Mối quan hệ:
 * - FK UserId → auth.Users (nhân viên bán hàng)
 * - FK ShiftId → sales.Shifts (ca làm việc, nullable)
 * - FK CustomerId → catalog.Customers (khách hàng, nullable)
 * - FK PrescriptionId → sales.Prescriptions (đơn thuốc, nullable)
 * - 1:N InvoiceDetails (chi tiết dòng thuốc)
 * 
 * Trạng thái (Status):
 * - DRAFT: Hóa đơn đang soạn thảo (chưa thanh toán)
 * - PROCESSING: Hóa đơn được xác nhận, đang gọi Inventory trừ tồn
 * - COMPLETED: Trừ tồn thành công, hóa đơn hoàn tất
 * - CANCELLED: Hóa đơn bị hủy, tồn đã hoàn trả
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class Invoice {
    
    public enum InvoiceStatus {
        DRAFT,         // Đang soạn thảo
        PROCESSING,    // Đang xử lý (gọi Inventory chưa xong)
        COMPLETED,     // Hoàn tất
        CANCELLED      // Hủy
    }
    
    public enum PaymentMethod {
        CASH,          // Tiền mặt
        BANK_TRANSFER, // Chuyển khoản
        CARD           // Thẻ
    }
    
    private Long invoiceId;           // Primary Key
    private String invoiceCode;       // Unique: <mã_chi_nhánh>-<yyyyMMdd>-<số_thứ_tự>
    private Long branchId;            // FK -> catalog.Branches
    private Long userId;              // FK -> auth.Users (nhân viên bán hàng)
    private Long shiftId;             // FK -> sales.Shifts (nullable)
    private Long customerId;          // FK -> catalog.Customers (nullable)
    private Long prescriptionId;      // FK -> sales.Prescriptions (nullable)
    
    // Thông tin hóa đơn
    private LocalDateTime invoiceDate;  // Ngày lập hóa đơn
    private InvoiceStatus status;       // DRAFT, PROCESSING, COMPLETED, CANCELLED
    
    // Dữ liệu tính toán
    private BigDecimal subtotal;        // Tổng tiền trước giảm giá (sum của tất cả dòng)
    private BigDecimal discountAmount;  // Tiền giảm giá
    private BigDecimal totalAmount;     // Tổng tiền = subtotal - discountAmount
    
    // Thanh toán
    private PaymentMethod paymentMethod;  // CASH, BANK_TRANSFER, CARD (nullable)
    
    // Ghi chú
    private String note;                // Ghi chú (nullable)
    
    // Constructors
    public Invoice() {
    }
    
    public Invoice(Long branchId, Long userId, String invoiceCode) {
        this.branchId = branchId;
        this.userId = userId;
        this.invoiceCode = invoiceCode;
        this.status = InvoiceStatus.DRAFT;
        this.invoiceDate = LocalDateTime.now();
        this.subtotal = BigDecimal.ZERO;
        this.discountAmount = BigDecimal.ZERO;
        this.totalAmount = BigDecimal.ZERO;
    }
    
    // Getters & Setters
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    
    public String getInvoiceCode() { return invoiceCode; }
    public void setInvoiceCode(String invoiceCode) { this.invoiceCode = invoiceCode; }
    
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    
    public Long getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(Long prescriptionId) { this.prescriptionId = prescriptionId; }
    
    public LocalDateTime getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDateTime invoiceDate) { this.invoiceDate = invoiceDate; }
    
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }
    
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    
    // Business Methods
    /**
     * Tính lại totalAmount = subtotal - discountAmount
     */
    public void calculateTotal() {
        if (subtotal != null && discountAmount != null) {
            this.totalAmount = subtotal.subtract(discountAmount);
            if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
                this.totalAmount = BigDecimal.ZERO;
            }
        }
    }
    
    @Override
    public String toString() {
        return "Invoice{" +
                "invoiceId=" + invoiceId +
                ", invoiceCode='" + invoiceCode + '\'' +
                ", branchId=" + branchId +
                ", status=" + status +
                ", totalAmount=" + totalAmount +
                ", invoiceDate=" + invoiceDate +
                '}';
    }
}

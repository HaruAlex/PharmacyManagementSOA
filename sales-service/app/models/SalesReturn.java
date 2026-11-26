package models;

import java.time.LocalDateTime;

/**
 * SalesReturn Model - Phiếu trả hàng bán lẻ
 * 
 * Database Table: sales.SalesReturns
 * Mối quan hệ:
 * - FK InvoiceId → sales.Invoices (hóa đơn gốc)
 * - FK CreatedBy → auth.Users (người lập phiếu trả)
 * - 1:N SalesReturnDetails (chi tiết từng dòng trả)
 * 
 * Dùng để xử lý trả hàng theo hóa đơn gốc
 * Có thể hoàn tồn hoặc không tùy vào lý do (hỏng, hết hạn, v.v.)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class SalesReturn {
    
    public enum SalesReturnStatus {
        DRAFT,      // Đang soạn thảo
        APPROVED,   // Đã duyệt
        CANCELLED   // Hủy
    }
    
    private Long salesReturnId;        // Primary Key
    private String returnCode;         // Unique: mã phiếu trả
    private Long invoiceId;            // FK -> sales.Invoices
    
    // Thông tin
    private Long createdBy;            // FK -> auth.Users (người lập)
    private LocalDateTime returnDate;  // Ngày lập phiếu trả
    
    // Trạng thái
    private SalesReturnStatus status;  // DRAFT, APPROVED, CANCELLED
    
    // Lý do
    private String returnReasonCode;   // Mã lý do trả (tùy chọn)
    private String note;               // Ghi chú thêm
    
    // Constructors
    public SalesReturn() {
    }
    
    public SalesReturn(String returnCode, Long invoiceId, Long createdBy) {
        this.returnCode = returnCode;
        this.invoiceId = invoiceId;
        this.createdBy = createdBy;
        this.returnDate = LocalDateTime.now();
        this.status = SalesReturnStatus.DRAFT;
    }
    
    // Getters & Setters
    public Long getSalesReturnId() { return salesReturnId; }
    public void setSalesReturnId(Long salesReturnId) { this.salesReturnId = salesReturnId; }
    
    public String getReturnCode() { return returnCode; }
    public void setReturnCode(String returnCode) { this.returnCode = returnCode; }
    
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    
    public LocalDateTime getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDateTime returnDate) { this.returnDate = returnDate; }
    
    public SalesReturnStatus getStatus() { return status; }
    public void setStatus(SalesReturnStatus status) { this.status = status; }
    
    public String getReturnReasonCode() { return returnReasonCode; }
    public void setReturnReasonCode(String returnReasonCode) { this.returnReasonCode = returnReasonCode; }
    
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    
    // Business Methods
    /**
     * Kiểm tra phiếu có thể duyệt được không
     */
    public boolean canApprove() {
        return this.status == SalesReturnStatus.DRAFT;
    }
    
    /**
     * Kiểm tra phiếu có thể hủy được không
     */
    public boolean canCancel() {
        return this.status != SalesReturnStatus.CANCELLED;
    }
    
    @Override
    public String toString() {
        return "SalesReturn{" +
                "salesReturnId=" + salesReturnId +
                ", returnCode='" + returnCode + '\'' +
                ", invoiceId=" + invoiceId +
                ", status=" + status +
                ", returnDate=" + returnDate +
                '}';
    }
}

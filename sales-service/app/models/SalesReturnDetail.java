package models;

import java.math.BigDecimal;

/**
 * SalesReturnDetail Model - Chi tiết trả hàng bán lẻ
 * 
 * Database Table: sales.SalesReturnDetails
 * Mối quan hệ:
 * - FK SalesReturnId → sales.SalesReturns
 * - FK InvoiceDetailId → sales.InvoiceDetails
 * 
 * Mỗi dòng trả hàng liên kết với 1 dòng hóa đơn gốc
 * Có thể trả một phần số lượng
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class SalesReturnDetail {
    
    private Long salesReturnDetailId;  // Primary Key
    private Long salesReturnId;        // FK -> sales.SalesReturns
    private Long invoiceDetailId;      // FK -> sales.InvoiceDetails
    
    // Dữ liệu trả
    private BigDecimal quantityBase;   // Số lượng trả (đơn vị cơ sở)
    
    // Tùy chọn nhập lại kho
    private BigDecimal restockQuantity; // Số lượng được nhập lại kho (nullable)
    
    // Constructors
    public SalesReturnDetail() {
    }
    
    public SalesReturnDetail(Long salesReturnId, Long invoiceDetailId, BigDecimal quantityBase) {
        this.salesReturnId = salesReturnId;
        this.invoiceDetailId = invoiceDetailId;
        this.quantityBase = quantityBase;
    }
    
    // Getters & Setters
    public Long getSalesReturnDetailId() { return salesReturnDetailId; }
    public void setSalesReturnDetailId(Long salesReturnDetailId) { this.salesReturnDetailId = salesReturnDetailId; }
    
    public Long getSalesReturnId() { return salesReturnId; }
    public void setSalesReturnId(Long salesReturnId) { this.salesReturnId = salesReturnId; }
    
    public Long getInvoiceDetailId() { return invoiceDetailId; }
    public void setInvoiceDetailId(Long invoiceDetailId) { this.invoiceDetailId = invoiceDetailId; }
    
    public BigDecimal getQuantityBase() { return quantityBase; }
    public void setQuantityBase(BigDecimal quantityBase) { this.quantityBase = quantityBase; }
    
    public BigDecimal getRestockQuantity() { return restockQuantity; }
    public void setRestockQuantity(BigDecimal restockQuantity) { this.restockQuantity = restockQuantity; }
    
    @Override
    public String toString() {
        return "SalesReturnDetail{" +
                "salesReturnDetailId=" + salesReturnDetailId +
                ", salesReturnId=" + salesReturnId +
                ", invoiceDetailId=" + invoiceDetailId +
                ", quantityBase=" + quantityBase +
                ", restockQuantity=" + restockQuantity +
                '}';
    }
}

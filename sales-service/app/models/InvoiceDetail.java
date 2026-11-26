package models;

import java.math.BigDecimal;

/**
 * InvoiceDetail Model - Chi tiết dòng thuốc trong hóa đơn
 * 
 * Database Table: sales.InvoiceDetails
 * Mối quan hệ:
 * - FK InvoiceId → sales.Invoices
 * - FK DrugId → catalog.Drugs
 * - FK UnitId → catalog.Units
 * - 1:N InvoiceDetailLots (chi tiết lô FEFO)
 * 
 * Mỗi dòng thuốc có thể tách thành nhiều lô 
 * (ví dụ: bán 1500 viên từ 2-3 lô khác nhau)
 * 
 * Giá bán được lưu tại thời điểm bán để có thể 
 * tính lợi nhuận chính xác trong báo cáo
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class InvoiceDetail {
    
    private Long invoiceDetailId;     // Primary Key
    private Long invoiceId;           // FK -> sales.Invoices
    private Long drugId;              // FK -> catalog.Drugs
    private Long unitId;              // FK -> catalog.Units
    
    // Dữ liệu bán
    private BigDecimal quantity;      // Số lượng theo đơn vị (ví dụ: 2 vỉ)
    private BigDecimal quantityBase;  // Số lượng đơn vị cơ sở (ví dụ: 2 vỉ = 20 viên)
    
    // Giá (lưu tại thời điểm bán)
    private BigDecimal unitPrice;     // Giá bán 1 đơn vị
    private BigDecimal discountAmount;// Giảm giá cho dòng này (nếu có)
    private BigDecimal lineTotal;     // Tổng = (quantity * unitPrice) - discountAmount
    
    // Constructors
    public InvoiceDetail() {
    }
    
    public InvoiceDetail(Long invoiceId, Long drugId, Long unitId,
                        BigDecimal quantity, BigDecimal quantityBase) {
        this.invoiceId = invoiceId;
        this.drugId = drugId;
        this.unitId = unitId;
        this.quantity = quantity;
        this.quantityBase = quantityBase;
        this.discountAmount = BigDecimal.ZERO;
    }
    
    // Getters & Setters
    public Long getInvoiceDetailId() { return invoiceDetailId; }
    public void setInvoiceDetailId(Long invoiceDetailId) { this.invoiceDetailId = invoiceDetailId; }
    
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    
    public Long getDrugId() { return drugId; }
    public void setDrugId(Long drugId) { this.drugId = drugId; }
    
    public Long getUnitId() { return unitId; }
    public void setUnitId(Long unitId) { this.unitId = unitId; }
    
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    
    public BigDecimal getQuantityBase() { return quantityBase; }
    public void setQuantityBase(BigDecimal quantityBase) { this.quantityBase = quantityBase; }
    
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
    
    // Business Methods
    /**
     * Tính lại lineTotal = (quantity * unitPrice) - discountAmount
     */
    public void calculateLineTotal() {
        if (quantity != null && unitPrice != null) {
            BigDecimal subtotal = quantity.multiply(unitPrice);
            BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
            this.lineTotal = subtotal.subtract(discount);
            if (lineTotal.compareTo(BigDecimal.ZERO) < 0) {
                this.lineTotal = BigDecimal.ZERO;
            }
        }
    }
    
    @Override
    public String toString() {
        return "InvoiceDetail{" +
                "invoiceDetailId=" + invoiceDetailId +
                ", invoiceId=" + invoiceId +
                ", drugId=" + drugId +
                ", quantity=" + quantity +
                ", lineTotal=" + lineTotal +
                '}';
    }
}

package models;

import java.math.BigDecimal;

/**
 * InvoiceDetailLot Model - Chi tiết lô trong mỗi dòng hóa đơn (FEFO)
 * 
 * Database Table: sales.InvoiceDetailLots
 * Mối quan hệ:
 * - FK InvoiceDetailId → sales.InvoiceDetails
 * - FK LotId → inventory.DrugLots
 * 
 * Mỗi dòng hóa đơn có thể gồm nhiều lô khác nhau
 * (ví dụ: bán 1500 viên Paracetamol từ LOT-PARA-01 và LOT-PARA-02)
 * 
 * Lưu giá vốn tại thời điểm bán để có thể tính 
 * lợi nhuận chính xác cho báo cáo
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class InvoiceDetailLot {
    
    private Long invoiceDetailLotId;   // Primary Key
    private Long invoiceDetailId;      // FK -> sales.InvoiceDetails
    private Long lotId;                // FK -> inventory.DrugLots
    
    // Dữ liệu trừ tồn
    private BigDecimal quantityBase;   // Số lượng đã trừ (đơn vị cơ sở)
    
    // Giá vốn tại thời bán (để tính lợi nhuận)
    private BigDecimal costBaseAtSale; // Giá vốn 1 đơn vị cơ sở tại thời bán
    
    // Constructors
    public InvoiceDetailLot() {
    }
    
    public InvoiceDetailLot(Long invoiceDetailId, Long lotId, 
                           BigDecimal quantityBase, BigDecimal costBaseAtSale) {
        this.invoiceDetailId = invoiceDetailId;
        this.lotId = lotId;
        this.quantityBase = quantityBase;
        this.costBaseAtSale = costBaseAtSale;
    }
    
    // Getters & Setters
    public Long getInvoiceDetailLotId() { return invoiceDetailLotId; }
    public void setInvoiceDetailLotId(Long invoiceDetailLotId) { this.invoiceDetailLotId = invoiceDetailLotId; }
    
    public Long getInvoiceDetailId() { return invoiceDetailId; }
    public void setInvoiceDetailId(Long invoiceDetailId) { this.invoiceDetailId = invoiceDetailId; }
    
    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    
    public BigDecimal getQuantityBase() { return quantityBase; }
    public void setQuantityBase(BigDecimal quantityBase) { this.quantityBase = quantityBase; }
    
    public BigDecimal getCostBaseAtSale() { return costBaseAtSale; }
    public void setCostBaseAtSale(BigDecimal costBaseAtSale) { this.costBaseAtSale = costBaseAtSale; }
    
    // Business Methods
    /**
     * Tính giá vốn total = quantityBase * costBaseAtSale
     */
    public BigDecimal calculateTotalCost() {
        if (quantityBase != null && costBaseAtSale != null) {
            return quantityBase.multiply(costBaseAtSale);
        }
        return BigDecimal.ZERO;
    }
    
    @Override
    public String toString() {
        return "InvoiceDetailLot{" +
                "invoiceDetailLotId=" + invoiceDetailLotId +
                ", invoiceDetailId=" + invoiceDetailId +
                ", lotId=" + lotId +
                ", quantityBase=" + quantityBase +
                ", costBaseAtSale=" + costBaseAtSale +
                '}';
    }
}

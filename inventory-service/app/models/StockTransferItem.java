package models;

/**
 * Dòng chi tiết thuốc/lô trong phiếu chuyển kho (Stock Transfer).
 */
public class StockTransferItem {
    private Long id;
    private Long transferId;      // ID phiếu chuyển kho cha
    private Integer drugId;         // Mã thuốc
    private Integer lotId;          // Mã lô thuốc (LoThuoc)
    private String lotNumber;       // Số lô (hiển thị khi tra cứu)
    private Integer quantity;       // Số lượng chuyển theo đơn vị cơ sở (> 0)

    public StockTransferItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }

    public Integer getDrugId() { return drugId; }
    public void setDrugId(Integer drugId) { this.drugId = drugId; }

    public Integer getLotId() { return lotId; }
    public void setLotId(Integer lotId) { this.lotId = lotId; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
package models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thực thể (Entity) ánh xạ bảng BienDongKho (Thẻ kho / Stock Ledger) trong SQL Server.
 * Lưu vết mọi biến động tồn kho theo chi nhánh, thuốc và từng lô cụ thể.
 */
public class StockLedger {
    private Long id;
    private Integer branchId;          // Mã chi nhánh phát sinh biến động
    private Integer drugId;            // Mã thuốc
    private Integer lotId;             // Mã lô thuốc (LoThuoc)
    private String movementType;       // Loại biến động: NHAP, BAN, TRA_KH, TRA_NCC, HUY, CHUYEN_XUAT, CHUYEN_NHAN, DIEU_CHINH
    private Integer quantityChange;    // Số lượng thay đổi theo đơn vị cơ sở (+ khi tăng tồn, - khi giảm tồn)
    private Integer balanceAfter;      // Tồn kho của lô tại chi nhánh ngay sau khi ghi nhận biến động
    private BigDecimal unitCost;       // Giá vốn tại thời điểm giao dịch (nếu có)
    private String referenceType;      // Loại chứng từ tham chiếu: INVOICE, RECEIPT, TRANSFER, STOCKTAKE, DISPOSAL...
    private Long referenceId;          // ID bản ghi chứng từ gốc
    private String referenceCode;      // Mã chứng từ hiển thị (VD: CN02-20261126-0001)
    private String note;               // Ghi chú / lý do biến động
    private Integer createdBy;         // ID người dùng thực hiện thao tác
    private LocalDateTime createdAt;   // Thời điểm ghi nhận biến động

    public StockLedger() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getBranchId() { return branchId; }
    public void setBranchId(Integer branchId) { this.branchId = branchId; }

    public Integer getDrugId() { return drugId; }
    public void setDrugId(Integer drugId) { this.drugId = drugId; }

    public Integer getLotId() { return lotId; }
    public void setLotId(Integer lotId) { this.lotId = lotId; }

    public String getMovementType() { return movementType; }
    public void setMovementType(String movementType) { this.movementType = movementType; }

    public Integer getQuantityChange() { return quantityChange; }
    public void setQuantityChange(Integer quantityChange) { this.quantityChange = quantityChange; }

    public Integer getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(Integer balanceAfter) { this.balanceAfter = balanceAfter; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }

    public String getReferenceCode() { return referenceCode; }
    public void setReferenceCode(String referenceCode) { this.referenceCode = referenceCode; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
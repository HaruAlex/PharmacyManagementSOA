package models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Thực thể (Entity) ánh xạ phiếu chuyển kho (Stock Transfer) giữa hai chi nhánh.
 */
public class StockTransfer {
    private Long id;
    private String transferCode;          // Mã phiếu chuyển kho (VD: TF-20261126-0001)
    private Integer sourceBranchId;       // Chi nhánh xuất kho
    private Integer targetBranchId;       // Chi nhánh nhận kho
    private String status;                // Trạng thái phiếu (VD: COMPLETED, PENDING, CANCELLED)
    private String note;                  // Ghi chú chuyển kho
    private Integer createdBy;            // ID người tạo phiếu
    private LocalDateTime createdAt;      // Thời điểm tạo phiếu
    private List<StockTransferItem> items = new ArrayList<>(); // Danh sách lô thuốc chuyển

    public StockTransfer() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTransferCode() { return transferCode; }
    public void setTransferCode(String transferCode) { this.transferCode = transferCode; }

    public Integer getSourceBranchId() { return sourceBranchId; }
    public void setSourceBranchId(Integer sourceBranchId) { this.sourceBranchId = sourceBranchId; }

    public Integer getTargetBranchId() { return targetBranchId; }
    public void setTargetBranchId(Integer targetBranchId) { this.targetBranchId = targetBranchId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<StockTransferItem> getItems() { return items; }
    public void setItems(List<StockTransferItem> items) { this.items = items; }
}
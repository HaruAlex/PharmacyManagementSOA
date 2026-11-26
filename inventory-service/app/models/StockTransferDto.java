package models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO đại diện một phiếu chuyển kho trong danh sách / chi tiết trả về của API BE-31.
 */
public class StockTransferDto {
    public Long id;
    public String transferCode;
    public Integer sourceBranchId;
    public Integer targetBranchId;
    public String status;
    public String note;
    public Integer createdBy;
    public LocalDateTime createdAt;
    public List<StockTransferItem> items = new ArrayList<>();

    public StockTransferDto() {}
}
package models;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO trả về cho API tra cứu thẻ kho (GET /api/v1/inventory/ledger).
 * Bổ sung thêm số lô và hạn dùng từ bảng LoThuoc để phục vụ tra cứu nhanh trên giao diện.
 */
public class StockLedgerDto {
    public Long id;
    public Integer branchId;
    public Integer drugId;
    public Integer lotId;
    public String lotNumber;          // Số lô (lấy từ LoThuoc.so_lo)
    public LocalDate expiryDate;      // Hạn dùng của lô (lấy từ LoThuoc.han_dung)
    public String movementType;       // Loại biến động kho
    public Integer quantityChange;    // Số lượng biến động (+ / -)
    public Integer balanceAfter;      // Tồn lô sau biến động
    public BigDecimal unitCost;       // Giá vốn
    public String referenceType;      // Loại chứng từ
    public Long referenceId;          // ID chứng từ
    public String referenceCode;      // Mã chứng từ
    public String note;               // Ghi chú
    public Integer createdBy;         // Người thực hiện
    public LocalDateTime createdAt;   // Thời gian biến động

    public StockLedgerDto() {}
}
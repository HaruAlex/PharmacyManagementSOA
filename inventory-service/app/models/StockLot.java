package models;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Model đại diện cho thông tin tồn kho của một lô thuốc cụ thể (Stock By Lot).
 */
public class StockLot {

    // Mã lô thuốc (ví dụ: LOT-2026-01)
    private String lotNumber;

    // Mã thuốc thuộc lô này (ví dụ: MED-01)
    private String medicineId;

    // Tên thuốc để tiện hiển thị/tìm kiếm
    private String medicineName;

    // Đơn vị tính (ví dụ: Hop, Vi, Chai)
    private String unit;

    // Số lượng tồn hiện tại của riêng lô này
    private int quantity;

    // Ngưỡng cảnh báo sắp hết hàng theo mức cấu hình của lô/thuốc
    private int minStockThreshold;

    // Ngày hết hạn của lô thuốc
    private LocalDate expiryDate;

    // Thời điểm cập nhật lô gần nhất (UTC)
    private Instant updatedAt;

    public StockLot() {
    }

    public StockLot(
            String lotNumber,
            String medicineId,
            String medicineName,
            String unit,
            int quantity,
            int minStockThreshold,
            LocalDate expiryDate,
            Instant updatedAt
    ) {
        this.lotNumber = lotNumber;
        this.medicineId = medicineId;
        this.medicineName = medicineName;
        this.unit = unit;
        this.quantity = quantity;
        this.minStockThreshold = minStockThreshold;
        this.expiryDate = expiryDate;
        this.updatedAt = updatedAt;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public String getMedicineId() {
        return medicineId;
    }

    public void setMedicineId(String medicineId) {
        this.medicineId = medicineId;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getMinStockThreshold() {
        return minStockThreshold;
    }

    public void setMinStockThreshold(int minStockThreshold) {
        this.minStockThreshold = minStockThreshold;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Kiểm tra lô có tồn kho thấp hơn hoặc bằng ngưỡng tối thiểu hay không.
     */
    public boolean isLowStock() {
        return quantity <= minStockThreshold;
    }

    /**
     * Kiểm tra lô đã hết hạn tính theo ngày hiện tại hay chưa.
     */
    public boolean isExpired(LocalDate referenceDate) {
        return expiryDate != null && !expiryDate.isAfter(referenceDate);
    }
}
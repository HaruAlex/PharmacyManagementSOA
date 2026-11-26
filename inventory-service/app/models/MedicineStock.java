package models;

import java.time.Instant;

/**
 * Model đại diện cho thông tin tồn kho tổng hợp của một loại thuốc (BE-28).
 *
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class MedicineStock {

    private String medicineId;
    private String medicineName;
    private String unit;
    private int totalQuantity;
    private int minStockThreshold;
    private Instant updatedAt;

    public MedicineStock() {
    }

    public MedicineStock(
            String medicineId,
            String medicineName,
            String unit,
            int totalQuantity,
            int minStockThreshold,
            Instant updatedAt) {
        this.medicineId = medicineId;
        this.medicineName = medicineName;
        this.unit = unit;
        this.totalQuantity = totalQuantity;
        this.minStockThreshold = minStockThreshold;
        this.updatedAt = updatedAt;
    }

    /**
     * Kiểm tra tồn kho có thấp hơn hoặc bằng ngưỡng cảnh báo hay không.
     *
     * @return true nếu cần nhập thêm hàng
     */
    public boolean isLowStock() {
        return totalQuantity <= minStockThreshold;
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

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public int getMinStockThreshold() {
        return minStockThreshold;
    }

    public void setMinStockThreshold(int minStockThreshold) {
        this.minStockThreshold = minStockThreshold;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
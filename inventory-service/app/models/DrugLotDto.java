package models;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO đại diện cho thông tin tồn kho của một lô thuốc theo chi nhánh
 * và trạng thái hạn sử dụng.
 */
public class DrugLotDto {
    /** ID định danh duy nhất của lô thuốc. */
    private Long lotId;

    /** ID thuốc tương ứng trong danh mục. */
    private Long drugId;

    /** Mã định danh/mã quản lý của thuốc. */
    private String drugCode;

    /** Tên thương mại hoặc tên hoạt chất của thuốc. */
    private String drugName;

    /** Số lô do nhà sản xuất cấp. */
    private String lotNumber;

    /** Ngày sản xuất của lô thuốc. */
    private LocalDate manufactureDate;

    /** Ngày hết hạn của lô thuốc. */
    private LocalDate expiryDate;

    /** ID chi nhánh đang lưu trữ lô thuốc. */
    private Long branchId;

    /** Tên chi nhánh đang lưu trữ lô thuốc. */
    private String branchName;

    /** Số lượng tồn kho quy đổi theo đơn vị cơ sở. */
    private Long quantityBase;

    /** Giá vốn tính theo đơn vị cơ sở (dùng BigDecimal để tránh sai số tiền tệ). */
    private BigDecimal costBase;

    /** Trạng thái hạn sử dụng: CON_HAN (còn hạn), SAP_HET_HAN (sắp hết hạn), HET_HAN (hết hạn). */
    private String expiryStatus;

    /** @return ID lô thuốc. */
    public Long getLotId() { return lotId; }

    /** @param lotId ID lô thuốc cần gán. */
    public void setLotId(Long lotId) { this.lotId = lotId; }

    /** @return ID thuốc. */
    public Long getDrugId() { return drugId; }

    /** @param drugId ID thuốc cần gán. */
    public void setDrugId(Long drugId) { this.drugId = drugId; }

    /** @return mã thuốc. */
    public String getDrugCode() { return drugCode; }

    /** @param drugCode mã thuốc cần gán. */
    public void setDrugCode(String drugCode) { this.drugCode = drugCode; }

    /** @return tên thuốc. */
    public String getDrugName() { return drugName; }

    /** @param drugName tên thuốc cần gán. */
    public void setDrugName(String drugName) { this.drugName = drugName; }

    /** @return số lô sản xuất. */
    public String getLotNumber() { return lotNumber; }

    /** @param lotNumber số lô sản xuất cần gán. */
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    /** @return ngày sản xuất. */
    public LocalDate getManufactureDate() { return manufactureDate; }

    /** @param manufactureDate ngày sản xuất cần gán. */
    public void setManufactureDate(LocalDate manufactureDate) { this.manufactureDate = manufactureDate; }

    /** @return ngày hết hạn. */
    public LocalDate getExpiryDate() { return expiryDate; }

    /** @param expiryDate ngày hết hạn cần gán. */
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    /** @return ID chi nhánh. */
    public Long getBranchId() { return branchId; }

    /** @param branchId ID chi nhánh cần gán. */
    public void setBranchId(Long branchId) { this.branchId = branchId; }

    /** @return tên chi nhánh. */
    public String getBranchName() { return branchName; }

    /** @param branchName tên chi nhánh cần gán. */
    public void setBranchName(String branchName) { this.branchName = branchName; }

    /** @return số lượng tồn theo đơn vị cơ sở. */
    public Long getQuantityBase() { return quantityBase; }

    /** @param quantityBase số lượng tồn theo đơn vị cơ sở cần gán. */
    public void setQuantityBase(Long quantityBase) { this.quantityBase = quantityBase; }

    /** @return giá vốn theo đơn vị cơ sở. */
    public BigDecimal getCostBase() { return costBase; }

    /** @param costBase giá vốn theo đơn vị cơ sở cần gán. */
    public void setCostBase(BigDecimal costBase) { this.costBase = costBase; }

    /** @return trạng thái hạn sử dụng (CON_HAN, SAP_HET_HAN, HET_HAN). */
    public String getExpiryStatus() { return expiryStatus; }

    /** @param expiryStatus trạng thái hạn sử dụng cần gán. */
    public void setExpiryStatus(String expiryStatus) { this.expiryStatus = expiryStatus; }
}
package models;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * Prescription Model - Đơn thuốc kê đơn
 * 
 * Database Table: sales.Prescriptions
 * Mối quan hệ:
 * - FK CustomerId → catalog.Customers (nullable - khách hàng)
 * - FK CreatedBy → auth.Users (nhân viên/dược sĩ lập đơn)
 * - 1:N Invoices (các hóa đơn bán thuốc theo đơn này)
 * 
 * Dùng để lưu thông tin đơn thuốc kê đơn từ bác sĩ
 * Bắt buộc khi bán thuốc có PrescriptionRequired = true
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class Prescription {
    
    private Long prescriptionId;       // Primary Key
    private String prescriptionCode;   // Unique: mã đơn thuốc
    private Long customerId;           // FK -> catalog.Customers (nullable)
    
    // Thông tin bác sĩ & cơ sở
    private String prescriberName;     // Họ tên bác sĩ
    private String facilityName;       // Tên cơ sở khám (bệnh viện, phòng khám)
    
    // Thông tin đơn
    private LocalDate prescriptionDate; // Ngày kê đơn
    private String notes;              // Ghi chú thêm (từ bác sĩ)
    
    // Audit
    private Long createdBy;            // FK -> auth.Users (người lập trong hệ thống)
    private LocalDateTime createdAt;   // Thời gian lập trong hệ thống
    
    // Constructors
    public Prescription() {
    }
    
    public Prescription(String prescriptionCode, Long customerId, 
                       String prescriberName, LocalDate prescriptionDate, Long createdBy) {
        this.prescriptionCode = prescriptionCode;
        this.customerId = customerId;
        this.prescriberName = prescriberName;
        this.prescriptionDate = prescriptionDate;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters & Setters
    public Long getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(Long prescriptionId) { this.prescriptionId = prescriptionId; }
    
    public String getPrescriptionCode() { return prescriptionCode; }
    public void setPrescriptionCode(String prescriptionCode) { this.prescriptionCode = prescriptionCode; }
    
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    
    public String getPrescriberName() { return prescriberName; }
    public void setPrescriberName(String prescriberName) { this.prescriberName = prescriberName; }
    
    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) { this.facilityName = facilityName; }
    
    public LocalDate getPrescriptionDate() { return prescriptionDate; }
    public void setPrescriptionDate(LocalDate prescriptionDate) { this.prescriptionDate = prescriptionDate; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    @Override
    public String toString() {
        return "Prescription{" +
                "prescriptionId=" + prescriptionId +
                ", prescriptionCode='" + prescriptionCode + '\'' +
                ", prescriberName='" + prescriberName + '\'' +
                ", prescriptionDate=" + prescriptionDate +
                '}';
    }
}

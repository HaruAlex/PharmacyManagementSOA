package services;

import models.Prescription;
import repositories.PrescriptionRepository;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * PrescriptionService - Business logic cho đơn thuốc
 * 
 * Xử lý:
 * - Lập đơn thuốc mới
 * - Tìm kiếm đơn thuốc
 * - Cập nhật ghi chú
 * - Kiểm tra logic ràng buộc
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class PrescriptionService {
    
    private final PrescriptionRepository prescriptionRepository;
    
    @Inject
    public PrescriptionService(PrescriptionRepository prescriptionRepository) {
        this.prescriptionRepository = prescriptionRepository;
    }
    
    /**
     * UC-BH-03.1: Lấy đơn thuốc theo ID
     */
    public Optional<Prescription> getPrescriptionById(Long prescriptionId) {
        if (prescriptionId == null || prescriptionId <= 0) {
            return Optional.empty();
        }
        return prescriptionRepository.findById(prescriptionId);
    }
    
    /**
     * UC-BH-03.2: Lấy đơn thuốc theo mã đơn
     */
    public Optional<Prescription> getPrescriptionByCode(String prescriptionCode) {
        if (prescriptionCode == null || prescriptionCode.trim().isEmpty()) {
            return Optional.empty();
        }
        return prescriptionRepository.findByCode(prescriptionCode);
    }
    
    /**
     * UC-BH-03.3: Lập đơn thuốc mới
     * 
     * Validation:
     * - prescriptionCode != null, unique
     * - prescriberName != null
     * - prescriptionDate != null
     * - createdBy > 0
     */
    public Prescription createPrescription(Prescription prescription) throws IllegalArgumentException {
        // Validate input
        if (prescription == null) {
            throw new IllegalArgumentException("Prescription cannot be null");
        }
        if (prescription.getPrescriptionCode() == null || prescription.getPrescriptionCode().trim().isEmpty()) {
            throw new IllegalArgumentException("PrescriptionCode cannot be empty");
        }
        if (prescription.getPrescriberName() == null || prescription.getPrescriberName().trim().isEmpty()) {
            throw new IllegalArgumentException("PrescriberName cannot be empty");
        }
        if (prescription.getPrescriptionDate() == null) {
            throw new IllegalArgumentException("PrescriptionDate cannot be null");
        }
        if (prescription.getCreatedBy() == null || prescription.getCreatedBy() <= 0) {
            throw new IllegalArgumentException("CreatedBy must be > 0");
        }
        
        // Kiểm tra PrescriptionCode có trùng không
        Optional<Prescription> existing = prescriptionRepository.findByCode(prescription.getPrescriptionCode());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("PrescriptionCode already exists: " + prescription.getPrescriptionCode());
        }
        
        // Lập đơn thuốc
        if (prescription.getCreatedAt() == null) {
            prescription.setCreatedAt(LocalDateTime.now());
        }
        
        // Lưu vào database
        Long id = prescriptionRepository.create(prescription);
        if (id != null) {
            prescription.setPrescriptionId(id);
            return prescription;
        }
        
        throw new RuntimeException("Failed to create Prescription");
    }
    
    /**
     * UC-BH-03.4: Cập nhật ghi chú đơn thuốc
     */
    public Prescription updatePrescriptionNotes(Long prescriptionId, String notes) throws IllegalArgumentException {
        if (prescriptionId == null || prescriptionId <= 0) {
            throw new IllegalArgumentException("PrescriptionId must be > 0");
        }
        
        // Lấy đơn thuốc
        Optional<Prescription> optional = prescriptionRepository.findById(prescriptionId);
        if (!optional.isPresent()) {
            throw new IllegalArgumentException("Prescription not found with id: " + prescriptionId);
        }
        
        Prescription prescription = optional.get();
        prescription.setNotes(notes);
        
        // Cập nhật vào database
        boolean updated = prescriptionRepository.update(prescription);
        if (!updated) {
            throw new RuntimeException("Failed to update Prescription");
        }
        
        return prescription;
    }
    
    /**
     * UC-BH-03.5: Lấy tất cả đơn thuốc của một khách hàng
     */
    public List<Prescription> getPrescriptionsByCustomerId(Long customerId) {
        if (customerId == null || customerId <= 0) {
            return Collections.emptyList();
        }
        return prescriptionRepository.findByCustomerId(customerId);
    }
    
    /**
     * UC-BH-03.6: Lấy đơn thuốc theo ngày
     */
    public List<Prescription> getPrescriptionsByDate(LocalDate date) {
        if (date == null) {
            return Collections.emptyList();
        }
        return prescriptionRepository.findByPrescriptionDate(date);
    }
    
    /**
     * UC-BH-03.7: Lấy đơn thuốc theo người lập
     */
    public List<Prescription> getPrescriptionsByCreatedBy(Long userId) {
        if (userId == null || userId <= 0) {
            return Collections.emptyList();
        }
        return prescriptionRepository.findByCreatedBy(userId);
    }
    
    /**
     * UC-BH-03.8: Xóa đơn thuốc
     * 
     * Note: Chỉ xóa được đơn chưa có hóa đơn liên kết
     */
    public boolean deletePrescription(Long prescriptionId) throws IllegalArgumentException {
        if (prescriptionId == null || prescriptionId <= 0) {
            throw new IllegalArgumentException("PrescriptionId must be > 0");
        }
        
        // Lấy đơn thuốc
        Optional<Prescription> optional = prescriptionRepository.findById(prescriptionId);
        if (!optional.isPresent()) {
            throw new IllegalArgumentException("Prescription not found with id: " + prescriptionId);
        }
        
        return prescriptionRepository.delete(prescriptionId);
    }
    
    /**
     * UC-BH-03.9: Lấy số lượng đơn thuốc của một khách hàng
     */
    public int getPrescriptionCountByCustomerId(Long customerId) throws IllegalArgumentException {
        if (customerId == null || customerId <= 0) {
            throw new IllegalArgumentException("CustomerId must be > 0");
        }
        return prescriptionRepository.getCountByCustomerId(customerId);
    }
    
    /**
     * UC-BH-03.10: Kiểm tra khách hàng có đơn thuốc nào không
     */
    public boolean hasPrescriptions(Long customerId) throws IllegalArgumentException {
        if (customerId == null || customerId <= 0) {
            throw new IllegalArgumentException("CustomerId must be > 0");
        }
        return prescriptionRepository.getCountByCustomerId(customerId) > 0;
    }
}

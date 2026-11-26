package services;

import models.SalesReturn;
import repositories.SalesReturnRepository;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SalesReturnService - Business logic cho phiếu trả hàng
 * 
 * Xử lý:
 * - Lập phiếu trả mới
 * - Duyệt phiếu trả
 * - Hủy phiếu trả
 * - Tìm kiếm phiếu trả
 * - Kiểm tra logic ràng buộc
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class SalesReturnService {
    
    private final SalesReturnRepository salesReturnRepository;
    
    @Inject
    public SalesReturnService(SalesReturnRepository salesReturnRepository) {
        this.salesReturnRepository = salesReturnRepository;
    }
    
    /**
     * UC-BH-07.1: Lấy phiếu trả theo ID
     */
    public Optional<SalesReturn> getSalesReturnById(Long salesReturnId) {
        if (salesReturnId == null || salesReturnId <= 0) {
            return Optional.empty();
        }
        return salesReturnRepository.findById(salesReturnId);
    }
    
    /**
     * UC-BH-07.2: Lấy phiếu trả theo mã phiếu
     */
    public Optional<SalesReturn> getSalesReturnByCode(String returnCode) {
        if (returnCode == null || returnCode.trim().isEmpty()) {
            return Optional.empty();
        }
        return salesReturnRepository.findByReturnCode(returnCode);
    }
    
    /**
     * UC-BH-07.3: Lập phiếu trả mới
     * 
     * Validation:
     * - returnCode != null, unique
     * - invoiceId > 0
     * - createdBy > 0
     * - Status = DRAFT
     */
    public SalesReturn createSalesReturn(SalesReturn salesReturn) throws IllegalArgumentException {
        // Validate input
        if (salesReturn == null) {
            throw new IllegalArgumentException("SalesReturn cannot be null");
        }
        if (salesReturn.getReturnCode() == null || salesReturn.getReturnCode().trim().isEmpty()) {
            throw new IllegalArgumentException("ReturnCode cannot be empty");
        }
        if (salesReturn.getInvoiceId() == null || salesReturn.getInvoiceId() <= 0) {
            throw new IllegalArgumentException("InvoiceId must be > 0");
        }
        if (salesReturn.getCreatedBy() == null || salesReturn.getCreatedBy() <= 0) {
            throw new IllegalArgumentException("CreatedBy must be > 0");
        }
        
        // Kiểm tra ReturnCode có trùng không
        Optional<SalesReturn> existing = salesReturnRepository.findByReturnCode(salesReturn.getReturnCode());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("ReturnCode already exists: " + salesReturn.getReturnCode());
        }
        
        // Lập phiếu trả
        if (salesReturn.getReturnDate() == null) {
            salesReturn.setReturnDate(LocalDateTime.now());
        }
        if (salesReturn.getStatus() == null) {
            salesReturn.setStatus(SalesReturn.SalesReturnStatus.DRAFT);
        }
        
        // Lưu vào database
        Long id = salesReturnRepository.create(salesReturn);
        if (id != null) {
            salesReturn.setSalesReturnId(id);
            return salesReturn;
        }
        
        throw new RuntimeException("Failed to create SalesReturn");
    }
    
    /**
     * UC-BH-07.4: Duyệt phiếu trả
     * 
     * Validation:
     * - Phiếu phải có status = DRAFT
     * - Phiếu phải có ít nhất 1 chi tiết trả
     */
    public SalesReturn approveSalesReturn(Long salesReturnId) throws IllegalArgumentException, IllegalStateException {
        if (salesReturnId == null || salesReturnId <= 0) {
            throw new IllegalArgumentException("SalesReturnId must be > 0");
        }
        
        // Lấy phiếu trả
        Optional<SalesReturn> optional = salesReturnRepository.findById(salesReturnId);
        if (!optional.isPresent()) {
            throw new IllegalArgumentException("SalesReturn not found with id: " + salesReturnId);
        }
        
        SalesReturn salesReturn = optional.get();
        
        // Kiểm tra status
        if (!salesReturn.canApprove()) {
            throw new IllegalStateException("SalesReturn cannot be approved with status: " + salesReturn.getStatus());
        }
        
        // Duyệt phiếu
        salesReturn.setStatus(SalesReturn.SalesReturnStatus.APPROVED);
        
        // Cập nhật vào database
        boolean updated = salesReturnRepository.update(salesReturn);
        if (!updated) {
            throw new RuntimeException("Failed to approve SalesReturn");
        }
        
        return salesReturn;
    }
    
    /**
     * UC-BH-07.5: Hủy phiếu trả
     */
    public SalesReturn cancelSalesReturn(Long salesReturnId, String cancelReason) throws IllegalArgumentException, IllegalStateException {
        if (salesReturnId == null || salesReturnId <= 0) {
            throw new IllegalArgumentException("SalesReturnId must be > 0");
        }
        
        // Lấy phiếu trả
        Optional<SalesReturn> optional = salesReturnRepository.findById(salesReturnId);
        if (!optional.isPresent()) {
            throw new IllegalArgumentException("SalesReturn not found with id: " + salesReturnId);
        }
        
        SalesReturn salesReturn = optional.get();
        
        // Kiểm tra status
        if (!salesReturn.canCancel()) {
            throw new IllegalStateException("SalesReturn cannot be cancelled with status: " + salesReturn.getStatus());
        }
        
        // Hủy phiếu
        salesReturn.setStatus(SalesReturn.SalesReturnStatus.CANCELLED);
        salesReturn.setNote(cancelReason);
        
        // Cập nhật vào database
        boolean updated = salesReturnRepository.update(salesReturn);
        if (!updated) {
            throw new RuntimeException("Failed to cancel SalesReturn");
        }
        
        return salesReturn;
    }
    
    /**
     * UC-BH-07.6: Lấy tất cả phiếu trả của một hóa đơn
     */
    public List<SalesReturn> getSalesReturnsByInvoiceId(Long invoiceId) {
        if (invoiceId == null || invoiceId <= 0) {
            return Collections.emptyList();
        }
        return salesReturnRepository.findByInvoiceId(invoiceId);
    }
    
    /**
     * UC-BH-07.7: Lấy tất cả phiếu trả của một user
     */
    public List<SalesReturn> getSalesReturnsByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return Collections.emptyList();
        }
        return salesReturnRepository.findByCreatedBy(userId);
    }
    
    /**
     * UC-BH-07.8: Lấy tất cả phiếu trả có status = DRAFT
     */
    public List<SalesReturn> getDraftSalesReturns() {
        return salesReturnRepository.findByStatusDraft();
    }
    
    /**
     * UC-BH-07.9: Xóa phiếu trả
     * 
     * Thường chỉ xóa được phiếu ở trạng thái DRAFT
     */
    public boolean deleteSalesReturn(Long salesReturnId) throws IllegalArgumentException, IllegalStateException {
        if (salesReturnId == null || salesReturnId <= 0) {
            throw new IllegalArgumentException("SalesReturnId must be > 0");
        }
        
        // Lấy phiếu trả
        Optional<SalesReturn> optional = salesReturnRepository.findById(salesReturnId);
        if (!optional.isPresent()) {
            throw new IllegalArgumentException("SalesReturn not found with id: " + salesReturnId);
        }
        
        SalesReturn salesReturn = optional.get();
        
        // Chỉ xóa được phiếu DRAFT
        if (salesReturn.getStatus() != SalesReturn.SalesReturnStatus.DRAFT) {
            throw new IllegalStateException("Can only delete SalesReturn with status DRAFT");
        }
        
        return salesReturnRepository.delete(salesReturnId);
    }
    
    /**
     * UC-BH-07.10: Lấy số lượng phiếu trả của một hóa đơn
     */
    public int getSalesReturnCountByInvoiceId(Long invoiceId) throws IllegalArgumentException {
        if (invoiceId == null || invoiceId <= 0) {
            throw new IllegalArgumentException("InvoiceId must be > 0");
        }
        return salesReturnRepository.getCountByInvoiceId(invoiceId);
    }
}

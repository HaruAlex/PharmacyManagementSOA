package services;

import models.InvoiceDetail;
import repositories.InvoiceDetailRepository;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.*;

/**
 * InvoiceDetailService - Business logic cho chi tiết hóa đơn
 * 
 * Xử lý:
 * - Thêm/sửa/xóa chi tiết hóa đơn
 * - Tính toán lineTotal
 * - Kiểm tra logic ràng buộc
 * - Tính tổng tiền của hóa đơn
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class InvoiceDetailService {
    
    private final InvoiceDetailRepository invoiceDetailRepository;
    
    @Inject
    public InvoiceDetailService(InvoiceDetailRepository invoiceDetailRepository) {
        this.invoiceDetailRepository = invoiceDetailRepository;
    }
    
    /**
     * UC-BH-02.1: Lấy chi tiết hóa đơn theo ID
     */
    public Optional<InvoiceDetail> getInvoiceDetailById(Long invoiceDetailId) {
        if (invoiceDetailId == null || invoiceDetailId <= 0) {
            return Optional.empty();
        }
        return invoiceDetailRepository.findById(invoiceDetailId);
    }
    
    /**
     * UC-BH-02.2: Lấy tất cả chi tiết của một hóa đơn
     */
    public List<InvoiceDetail> getInvoiceDetailsByInvoiceId(Long invoiceId) {
        if (invoiceId == null || invoiceId <= 0) {
            return Collections.emptyList();
        }
        return invoiceDetailRepository.findByInvoiceId(invoiceId);
    }
    
    /**
     * UC-BH-02.3: Thêm chi tiết hóa đơn mới
     * 
     * Validation:
     * - invoiceId > 0
     * - drugId > 0
     * - unitId > 0
     * - quantity > 0
     * - quantityBase > 0
     * - unitPrice >= 0
     * - Tính lại lineTotal
     */
    public InvoiceDetail addInvoiceDetail(InvoiceDetail detail) throws IllegalArgumentException {
        // Validate input
        if (detail == null) {
            throw new IllegalArgumentException("InvoiceDetail cannot be null");
        }
        if (detail.getInvoiceId() == null || detail.getInvoiceId() <= 0) {
            throw new IllegalArgumentException("InvoiceId must be > 0");
        }
        if (detail.getDrugId() == null || detail.getDrugId() <= 0) {
            throw new IllegalArgumentException("DrugId must be > 0");
        }
        if (detail.getUnitId() == null || detail.getUnitId() <= 0) {
            throw new IllegalArgumentException("UnitId must be > 0");
        }
        if (detail.getQuantity() == null || detail.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be > 0");
        }
        if (detail.getQuantityBase() == null || detail.getQuantityBase().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("QuantityBase must be > 0");
        }
        if (detail.getUnitPrice() == null || detail.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("UnitPrice cannot be negative");
        }
        
        // Tính lại lineTotal
        detail.calculateLineTotal();
        
        // Lưu vào database
        Long id = invoiceDetailRepository.create(detail);
        if (id != null) {
            detail.setInvoiceDetailId(id);
            return detail;
        }
        
        throw new RuntimeException("Failed to create InvoiceDetail");
    }
    
    /**
     * UC-BH-02.4: Cập nhật chi tiết hóa đơn
     * 
     * Validation:
     * - invoiceDetailId > 0
     * - quantity > 0
     * - quantityBase > 0
     * - unitPrice >= 0
     * - Tính lại lineTotal
     */
    public InvoiceDetail updateInvoiceDetail(InvoiceDetail detail) throws IllegalArgumentException {
        // Validate input
        if (detail == null) {
            throw new IllegalArgumentException("InvoiceDetail cannot be null");
        }
        if (detail.getInvoiceDetailId() == null || detail.getInvoiceDetailId() <= 0) {
            throw new IllegalArgumentException("InvoiceDetailId must be > 0");
        }
        if (detail.getQuantity() == null || detail.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be > 0");
        }
        if (detail.getQuantityBase() == null || detail.getQuantityBase().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("QuantityBase must be > 0");
        }
        if (detail.getUnitPrice() == null || detail.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("UnitPrice cannot be negative");
        }
        
        // Kiểm tra detail có tồn tại không
        Optional<InvoiceDetail> existing = invoiceDetailRepository.findById(detail.getInvoiceDetailId());
        if (!existing.isPresent()) {
            throw new IllegalArgumentException("InvoiceDetail not found with id: " + detail.getInvoiceDetailId());
        }
        
        // Tính lại lineTotal
        detail.calculateLineTotal();
        
        // Cập nhật vào database
        boolean updated = invoiceDetailRepository.update(detail);
        if (!updated) {
            throw new RuntimeException("Failed to update InvoiceDetail");
        }
        
        return detail;
    }
    
    /**
     * UC-BH-02.5: Xóa chi tiết hóa đơn
     */
    public boolean deleteInvoiceDetail(Long invoiceDetailId) throws IllegalArgumentException {
        if (invoiceDetailId == null || invoiceDetailId <= 0) {
            throw new IllegalArgumentException("InvoiceDetailId must be > 0");
        }
        
        // Kiểm tra detail có tồn tại không
        Optional<InvoiceDetail> detail = invoiceDetailRepository.findById(invoiceDetailId);
        if (!detail.isPresent()) {
            throw new IllegalArgumentException("InvoiceDetail not found with id: " + invoiceDetailId);
        }
        
        return invoiceDetailRepository.delete(invoiceDetailId);
    }
    
    /**
     * UC-BH-02.6: Tính tổng tiền của hóa đơn (SUM(LineTotal))
     */
    public BigDecimal getInvoiceTotalAmount(Long invoiceId) throws IllegalArgumentException {
        if (invoiceId == null || invoiceId <= 0) {
            throw new IllegalArgumentException("InvoiceId must be > 0");
        }
        return invoiceDetailRepository.getTotalByInvoiceId(invoiceId);
    }
    
    /**
     * UC-BH-02.7: Lấy số lượng chi tiết trong hóa đơn
     */
    public int getInvoiceDetailCount(Long invoiceId) throws IllegalArgumentException {
        if (invoiceId == null || invoiceId <= 0) {
            throw new IllegalArgumentException("InvoiceId must be > 0");
        }
        return invoiceDetailRepository.getCountByInvoiceId(invoiceId);
    }
    
    /**
     * UC-BH-02.8: Xóa tất cả chi tiết của một hóa đơn
     * 
     * Thường dùng khi hủy hóa đơn
     */
    public int deleteAllInvoiceDetails(Long invoiceId) throws IllegalArgumentException {
        if (invoiceId == null || invoiceId <= 0) {
            throw new IllegalArgumentException("InvoiceId must be > 0");
        }
        return invoiceDetailRepository.deleteByInvoiceId(invoiceId);
    }
    
    /**
     * UC-BH-02.9: Tính tổng lợi nhuận của hóa đơn
     * 
     * Giả định: có thêm field costPrice ở model Drugs
     * Profit = (unitPrice - costPrice) * quantity
     */
    public BigDecimal calculateProfitByInvoiceId(Long invoiceId) throws IllegalArgumentException {
        if (invoiceId == null || invoiceId <= 0) {
            throw new IllegalArgumentException("InvoiceId must be > 0");
        }
        
        List<InvoiceDetail> details = invoiceDetailRepository.findByInvoiceId(invoiceId);
        BigDecimal totalProfit = BigDecimal.ZERO;
        
        // Note: Tại đây giả định costPrice = 0 vì chưa có dữ liệu từ catalog.Drugs
        // Khi tích hợp với catalog service, sẽ cần fetch costPrice từ đó
        for (InvoiceDetail detail : details) {
            BigDecimal profit = detail.getLineTotal(); // Tạm tính = lineTotal
            totalProfit = totalProfit.add(profit);
        }
        
        return totalProfit;
    }
    
    /**
     * UC-BH-02.10: Kiểm tra hóa đơn có chi tiết nào không
     */
    public boolean hasInvoiceDetails(Long invoiceId) throws IllegalArgumentException {
        if (invoiceId == null || invoiceId <= 0) {
            throw new IllegalArgumentException("InvoiceId must be > 0");
        }
        return invoiceDetailRepository.getCountByInvoiceId(invoiceId) > 0;
    }
}

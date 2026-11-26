package services;

import models.Invoice;
import models.Invoice.InvoiceStatus;
import repositories.InvoiceRepository;
import clients.InventoryClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import java.math.BigDecimal;

/**
 * InvoiceService - Business Logic cho Invoice (Hóa đơn)
 * 
 * Chức năng:
 * 1. Lấy thông tin hóa đơn
 * 2. Hủy hóa đơn (Cancel Invoice)
 *    - Set Status = CANCELLED
 *    - Gọi Inventory API để hoàn trả tồn kho
 * 3. Lịch sử hóa đơn
 * 
 * Integration:
 * - InventoryClient: Gọi API hoàn trả tồn kho khi hủy
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class InvoiceService {
    
    private final InvoiceRepository invoiceRepository;
    private final InventoryClient inventoryClient;
    
    @Inject
    public InvoiceService(InvoiceRepository invoiceRepository, InventoryClient inventoryClient) {
        this.invoiceRepository = invoiceRepository;
        this.inventoryClient = inventoryClient;
    }
    
    /**
     * USE CASE 1: Lấy thông tin hóa đơn theo ID
     */
    public Optional<Invoice> getInvoiceById(Long invoiceId) {
        return invoiceRepository.findById(invoiceId);
    }
    
    /**
     * USE CASE 2: Lấy thông tin hóa đơn theo code
     */
    public Optional<Invoice> getInvoiceByCode(String invoiceCode) {
        return invoiceRepository.findByCode(invoiceCode);
    }
    
    /**
     * USE CASE 3: Lấy lịch sử hóa đơn của customer
     */
    public List<Invoice> getInvoicesByCustomerId(Long customerId, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;
        return invoiceRepository.findByCustomerId(customerId, page, pageSize);
    }
    
    /**
     * USE CASE 4: Lấy invoices theo status
     */
    public List<Invoice> getInvoicesByStatus(InvoiceStatus status, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;
        return invoiceRepository.findByStatus(status, page, pageSize);
    }
    
    /**
     * USE CASE 5: Lấy invoices của chi nhánh theo ngày (dùng báo cáo)
     */
    public List<Invoice> getInvoicesByBranchAndDate(Long branchId, LocalDate date) {
        return invoiceRepository.findByBranchAndDate(branchId, date);
    }
    
    /**
     * USE CASE 6: Hủy hóa đơn (Cancel Invoice) - CÓ TÍCH HỢP INVENTORY API
     * 
     * Flow:
     * 1. Lấy invoice từ DB
     * 2. Kiểm tra status (chỉ DRAFT, PROCESSING, COMPLETED được phép hủy)
     * 3. Gọi Inventory API để hoàn trả tồn kho
     * 4. Nếu Inventory API thành công → update status = CANCELLED
     * 5. Nếu Inventory API thất bại → throw exception, không hủy hóa đơn
     * 
     * @param invoiceId: ID hóa đơn cần hủy
     * @return true nếu hủy thành công
     */
    public boolean cancelInvoice(Long invoiceId) {
        // 1. Lấy invoice
        Optional<Invoice> invoice = invoiceRepository.findById(invoiceId);
        if (invoice.isEmpty()) {
            throw new IllegalArgumentException("Hóa đơn không tồn tại: " + invoiceId);
        }
        
        Invoice inv = invoice.get();
        
        // 2. Kiểm tra status - chỉ cho phép hủy nếu không phải CANCELLED
        if (inv.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalArgumentException("Hóa đơn đã hủy rồi: " + invoiceId);
        }
        
        // 3. Gọi Inventory API để hoàn trả tồn kho
        try {
            boolean restoreSuccess = inventoryClient.restoreStockByInvoiceId(inv.getBranchId(), invoiceId);
            
            if (!restoreSuccess) {
                throw new RuntimeException("Inventory API hoàn trả tồn kho thất bại");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi gọi Inventory API: " + e.getMessage(), e);
        }
        
        // 4. Update status = CANCELLED
        return invoiceRepository.cancel(invoiceId);
    }
    
    /**
     * USE CASE 7: Lấy tất cả invoices COMPLETED (dùng báo cáo doanh thu)
     */
    public List<Invoice> getCompletedInvoices(int page, int pageSize) {
        return getInvoicesByStatus(InvoiceStatus.COMPLETED, page, pageSize);
    }
    
    /**
     * USE CASE 8: Kiểm tra invoice có thể hủy không
     * - Chỉ hủy được nếu Status != CANCELLED
     */
    public boolean canCancelInvoice(Long invoiceId) {
        Optional<Invoice> invoice = invoiceRepository.findById(invoiceId);
        if (invoice.isEmpty()) {
            return false;
        }
        return invoice.get().getStatus() != InvoiceStatus.CANCELLED;
    }
    
    /**
     * USE CASE 9: Kiểm tra invoice có thể trả hàng không
     * - Chỉ trả được nếu Status = COMPLETED (hóa đơn hoàn tất)
     */
    public boolean canReturnInvoice(Long invoiceId) {
        Optional<Invoice> invoice = invoiceRepository.findById(invoiceId);
        if (invoice.isEmpty()) {
            return false;
        }
        return invoice.get().getStatus() == InvoiceStatus.COMPLETED;
    }
    
    /**
     * USE CASE 10: Lấy doanh thu theo ngày (subtotal tất cả invoices COMPLETED)
     */
    public BigDecimal getTotalRevenueByDate(Long branchId, LocalDate date) {
        List<Invoice> invoices = invoiceRepository.findByBranchAndDate(branchId, date);
        return invoices.stream()
            .filter(inv -> inv.getStatus() == InvoiceStatus.COMPLETED)
            .map(Invoice::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * USE CASE 11: Tạo hóa đơn mới (Lập hóa đơn) - BE-47
     * 
     * Flow:
     * 1. Validate dữ liệu đầu vào
     * 2. Sinh invoiceCode = <branchId>-<yyyyMMdd>-<seq>
     * 3. Create invoice với status = DRAFT
     * 4. Trả về invoice vừa tạo (invoiceId sẽ được set bởi database)
     * 
     * @param branchId Chi nhánh
     * @param userId Nhân viên tạo
     * @param shiftId Ca làm việc (nullable)
     * @param customerId Khách hàng (nullable)
     * @param prescriptionId Đơn thuốc (nullable)
     * @return Invoice vừa tạo
     */
    public Invoice createInvoice(Long branchId, Long userId, Long shiftId, Long customerId, Long prescriptionId) {
        // Validate
        if (branchId == null || userId == null) {
            throw new IllegalArgumentException("BranchId và UserId không được null");
        }
        
        // Tạo invoice code: <branchId>-<yyyyMMdd>-<seq>
        // Ví dụ: 2-20261126-001
        String invoiceCode = generateInvoiceCode(branchId);
        
        // Tạo invoice mới
        Invoice invoice = new Invoice(branchId, userId, invoiceCode);
        invoice.setShiftId(shiftId);
        invoice.setCustomerId(customerId);
        invoice.setPrescriptionId(prescriptionId);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setInvoiceDate(java.time.LocalDateTime.now());
        invoice.setSubtotal(BigDecimal.ZERO);
        invoice.setDiscountAmount(BigDecimal.ZERO);
        invoice.setTotalAmount(BigDecimal.ZERO);
        
        // Insert vào database
        Long invoiceId = invoiceRepository.create(invoice);
        invoice.setInvoiceId(invoiceId);
        
        return invoice;
    }
    
    /**
     * Generate invoice code: <branchId>-<yyyyMMdd>-<seq>
     * 
     * Ví dụ: 2-20261126-001
     * - 2: branchId
     * - 20261126: yyyyMMdd (date)
     * - 001: sequence number (sẽ tính từ database query để ensure uniqueness)
     */
    private String generateInvoiceCode(Long branchId) {
        java.time.LocalDate today = java.time.LocalDate.now();
        String dateStr = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // TODO: Query database để lấy seq number của hôm nay
        // Tạm thời hardcode seq = 001
        return String.format("%d-%s-001", branchId, dateStr);
    }
}

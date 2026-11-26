package services;

import models.Invoice;
import models.Invoice.InvoiceStatus;
import models.InvoiceDetail;
import models.InvoiceDetailLot;
import repositories.InvoiceDetailLotRepository;
import repositories.InvoiceDetailRepository;
import repositories.InvoiceRepository;
import clients.InventoryClient;
import clients.InventoryClientException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final InvoiceDetailRepository invoiceDetailRepository;
    private final InvoiceDetailLotRepository invoiceDetailLotRepository;
    private final InventoryClient inventoryClient;
    
    @Inject
    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceDetailRepository invoiceDetailRepository,
            InvoiceDetailLotRepository invoiceDetailLotRepository,
            InventoryClient inventoryClient) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceDetailRepository = invoiceDetailRepository;
        this.invoiceDetailLotRepository = invoiceDetailLotRepository;
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
        Optional<Invoice> invoice = invoiceRepository.findById(invoiceId);
        if (invoice.isEmpty()) {
            throw new IllegalArgumentException("Hóa đơn không tồn tại: " + invoiceId);
        }
        Invoice inv = invoice.get();
        if (inv.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalArgumentException("Hóa đơn đã hủy rồi: " + invoiceId);
        }
        if (inv.getStatus() == InvoiceStatus.PROCESSING) {
            throw new IllegalArgumentException("Hóa đơn đang xử lý tồn kho: " + invoiceId);
        }

        if (inv.getStatus() == InvoiceStatus.COMPLETED) {
            try {
                if (!inventoryClient.restoreStockByInvoiceId(inv.getBranchId(), invoiceId)) {
                    throw new RuntimeException("Inventory Service did not confirm stock restoration");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to restore invoice stock in Inventory Service", e);
            }
        }

        return invoiceRepository.cancel(invoiceId, inv.getStatus());
    }

    public JsonNode deductInvoiceStock(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Hóa đơn không tồn tại: " + invoiceId));
        if (invoice.getStatus() == InvoiceStatus.CANCELLED
                || invoice.getStatus() == InvoiceStatus.COMPLETED) {
            throw new IllegalArgumentException("Hóa đơn không ở trạng thái có thể trừ tồn kho");
        }

        List<InvoiceDetail> details = invoiceDetailRepository.findByInvoiceId(invoiceId);
        if (details.isEmpty()) {
            throw new IllegalArgumentException("Hóa đơn chưa có chi tiết thuốc");
        }
        validateInvoiceDetails(invoiceId, details);

        if (invoice.getStatus() == InvoiceStatus.DRAFT
                && !invoiceRepository.beginProcessing(invoiceId)) {
            Invoice current = invoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> new IllegalArgumentException("Hóa đơn không tồn tại: " + invoiceId));
            if (current.getStatus() != InvoiceStatus.PROCESSING) {
                throw new IllegalArgumentException("Hóa đơn không thể bắt đầu xử lý tồn kho");
            }
        }

        try {
            JsonNode response = inventoryClient.deductInvoiceStock(
                    invoiceId, invoice.getBranchId(), details);
            JsonNode success = response.get("success");
            if (success == null || !success.isBoolean() || !success.booleanValue()) {
                throw new IllegalStateException("Inventory Service returned an invalid deduction response");
            }

            List<InvoiceDetailLot> allocations = parseAllocations(response, invoiceId, details);
            invoiceDetailLotRepository.replaceByInvoiceId(invoiceId, allocations);
            if (!invoiceRepository.completeProcessing(invoiceId)) {
                Invoice current = invoiceRepository.findById(invoiceId)
                        .orElseThrow(() -> new IllegalStateException("Invoice disappeared during processing"));
                if (current.getStatus() != InvoiceStatus.COMPLETED) {
                    throw new IllegalStateException("Could not mark invoice as completed after stock deduction");
                }
            }
            return response;
        } catch (InventoryClientException e) {
            if (e.getStatusCode() == 409 && isInsufficientStock(e.getResponseBody())) {
                invoiceRepository.returnToDraft(invoiceId);
            }
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Invoice stock deduction could not be completed; retry while status is PROCESSING", e);
        }
    }

    private void validateInvoiceDetails(Long invoiceId, List<InvoiceDetail> details) {
        for (InvoiceDetail detail : details) {
            if (!invoiceId.equals(detail.getInvoiceId())
                    || detail.getInvoiceDetailId() == null
                    || detail.getDrugId() == null
                    || detail.getQuantityBase() == null
                    || detail.getQuantityBase().signum() <= 0) {
                throw new IllegalStateException("Invoice contains an invalid stock detail");
            }
        }
    }

    private List<InvoiceDetailLot> parseAllocations(
            JsonNode response, Long invoiceId, List<InvoiceDetail> details) {
        JsonNode allocationNodes = response.get("allocations");
        if (allocationNodes == null || !allocationNodes.isArray()) {
            throw new IllegalStateException("Inventory Service response is missing allocations");
        }
        Map<Long, InvoiceDetail> detailsById = new HashMap<>();
        Map<Long, java.math.BigDecimal> totals = new HashMap<>();
        for (InvoiceDetail detail : details) {
            detailsById.put(detail.getInvoiceDetailId(), detail);
        }

        List<InvoiceDetailLot> allocations = new ArrayList<>();
        for (JsonNode node : allocationNodes) {
            if (!node.isObject()) {
                throw new IllegalStateException("Inventory Service returned an invalid allocation");
            }
            long detailId = requiredPositiveLong(node.get("invoiceDetailId"));
            long drugId = requiredPositiveLong(node.get("drugId"));
            long lotId = requiredPositiveLong(node.get("lotId"));
            java.math.BigDecimal quantity = requiredPositiveDecimal(node.get("quantityBase"));
            java.math.BigDecimal cost = requiredNonNegativeDecimal(node.get("costBaseAtSale"));
            InvoiceDetail detail = detailsById.get(detailId);
            if (detail == null || detail.getDrugId() != drugId) {
                throw new IllegalStateException("Inventory allocation does not match an invoice detail");
            }
            totals.merge(detailId, quantity, java.math.BigDecimal::add);
            allocations.add(new InvoiceDetailLot(detailId, lotId, quantity, cost));
        }
        if (allocations.isEmpty()) {
            throw new IllegalStateException("Inventory Service returned no allocations");
        }
        for (InvoiceDetail detail : details) {
            java.math.BigDecimal allocated = totals.get(detail.getInvoiceDetailId());
            if (allocated == null || allocated.compareTo(detail.getQuantityBase()) != 0) {
                throw new IllegalStateException("Inventory allocation quantity does not match invoice detail");
            }
        }
        return allocations;
    }

    private long requiredPositiveLong(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() <= 0) {
            throw new IllegalStateException("Inventory Service returned an invalid allocation ID");
        }
        return value.longValue();
    }

    private java.math.BigDecimal requiredPositiveDecimal(JsonNode value) {
        if (value == null || !value.isNumber() || value.decimalValue().signum() <= 0) {
            throw new IllegalStateException("Inventory Service returned an invalid allocation quantity");
        }
        return value.decimalValue();
    }

    private java.math.BigDecimal requiredNonNegativeDecimal(JsonNode value) {
        if (value == null || !value.isNumber() || value.decimalValue().signum() < 0) {
            throw new IllegalStateException("Inventory Service returned an invalid allocation cost");
        }
        return value.decimalValue();
    }

    private boolean isInsufficientStock(String responseBody) {
        try {
            JsonNode response = play.libs.Json.parse(responseBody);
            JsonNode error = response.get("error");
            return error != null && error.isTextual()
                    && error.asText().startsWith("Insufficient eligible stock");
        } catch (Exception e) {
            return false;
        }
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
        return invoice.get().getStatus() == InvoiceStatus.DRAFT
                || invoice.get().getStatus() == InvoiceStatus.COMPLETED;
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

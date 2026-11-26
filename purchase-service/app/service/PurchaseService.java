package service;

import exception.PurchaseException;
import play.db.Database;
import play.mvc.Http;
import repository.PurchaseRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

@Singleton
public class PurchaseService {
    private final PurchaseRepository repository;

    @Inject
    public PurchaseService(PurchaseRepository repository) {
        this.repository = repository;
    }

    public long createPurchaseOrder(JsonNode body) {
        requireBody(body);
        int supplierId = requiredInt(body, "supplierId");
        int branchId = requiredInt(body, "branchId");
        int createdBy = requiredInt(body, "createdBy");
        requireSupplier(supplierId);
        requireBranch(branchId);
        requireUser(createdBy);
        JsonNode details = body.get("details");
        if (details == null || !details.isArray() || details.isEmpty()) {
            throw badRequest("Purchase order details must not be empty");
        }
        List<PurchaseRepository.OrderDetail> parsed = new ArrayList<>();
        for (JsonNode detail : details) parsed.add(parsePurchaseOrderDetail(detail));
        String code = uniqueCode(body, "orderCode", "PO");
        Timestamp date = body.has("orderDate") ? parseTimestamp(body.get("orderDate").asText(), "orderDate") : new Timestamp(System.currentTimeMillis());
        try {
            return repository.createPurchaseOrder(code, supplierId, branchId, createdBy, date,
                    text(body, "note"), parsed);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public List<PurchaseRepository.PurchaseRow> listPurchaseOrders(JsonNode ignored, JsonNode query) {
        return listOrders(text(query, "keyword"), intOptional(query, "supplierId"), text(query, "status"));
    }

    public List<PurchaseRepository.PurchaseRow> listOrders(String keyword, Integer supplierId, String status) {
        try {
            return repository.listPurchaseOrders(keyword, supplierId, status);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public PurchaseRepository.PurchaseRow getPurchaseOrder(long id) {
        try {
            PurchaseRepository.PurchaseRow row = repository.getPurchaseOrder(id);
            if (row == null) throw notFound("Purchase order not found");
            return row;
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public List<PurchaseRepository.DetailRow> getPurchaseOrderDetails(long id) {
        getPurchaseOrder(id);
        try {
            return repository.getPurchaseOrderDetails(id);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public long addPurchaseOrderDetail(long id, JsonNode body) {
        PurchaseRepository.PurchaseRow order = getPurchaseOrder(id);
        if (!"DRAFT".equals(order.status)) throw conflict("Purchase order details can only be changed while DRAFT");
        PurchaseRepository.OrderDetail detail = parsePurchaseOrderDetail(body);
        try {
            return repository.addPurchaseOrderDetail(id, detail);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public void updatePurchaseOrderDetail(long id, long detailId, JsonNode body) {
        PurchaseRepository.PurchaseRow order = getPurchaseOrder(id);
        if (!"DRAFT".equals(order.status)) throw conflict("Purchase order details can only be changed while DRAFT");
        PurchaseRepository.OrderDetail detail = parsePurchaseOrderDetail(body);
        try {
            if (!repository.updatePurchaseOrderDetail(id, detailId, detail)) {
                throw notFound("Purchase order detail not found");
            }
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public void deletePurchaseOrderDetail(long id, long detailId) {
        PurchaseRepository.PurchaseRow order = getPurchaseOrder(id);
        if (!"DRAFT".equals(order.status)) throw conflict("Purchase order details can only be changed while DRAFT");
        try {
            if (!repository.deletePurchaseOrderDetail(id, detailId)) {
                throw notFound("Purchase order detail not found");
            }
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public void updatePurchaseOrder(long id, JsonNode body) {
        PurchaseRepository.PurchaseRow current = getPurchaseOrder(id);
        requireBody(body);
        Integer supplierId = intOptional(body, "supplierId");
        Integer branchId = intOptional(body, "branchId");
        if (supplierId != null) requireSupplier(supplierId);
        if (branchId != null) requireBranch(branchId);
        Timestamp date = body.has("orderDate") ? parseTimestamp(body.get("orderDate").asText(), "orderDate") : null;
        String status = text(body, "status");
        if (status != null && !List.of("DRAFT", "SUBMITTED", "APPROVED", "CANCELLED").contains(status)) {
            throw badRequest("Invalid purchase order status");
        }
        boolean metadataChange = body.has("supplierId") || body.has("branchId")
                || body.has("orderDate") || body.has("note");
        if (metadataChange && !"DRAFT".equals(current.status)) {
            throw conflict("Only DRAFT purchase orders can be edited");
        }
        if (status != null && !status.equals(current.status)) {
            boolean validTransition = ("DRAFT".equals(current.status)
                    && ("SUBMITTED".equals(status) || "CANCELLED".equals(status)))
                    || ("SUBMITTED".equals(current.status)
                    && ("APPROVED".equals(status) || "CANCELLED".equals(status)));
            if (!validTransition) throw conflict("Invalid purchase order status transition");
        }
        try {
            if (!repository.updatePurchaseOrder(id, supplierId, branchId, date, body.has("note"),
                    text(body, "note"), status, current.status)) {
                throw conflict("Purchase order was changed; reload and retry");
            }
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public void cancelPurchaseOrder(long id) {
        getPurchaseOrder(id);
        try {
            if (!repository.cancelPurchaseOrder(id)) throw conflict("Only DRAFT purchase order can be cancelled");
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public long createReceipt(JsonNode body) {
        requireBody(body);
        int supplierId = requiredInt(body, "supplierId");
        int branchId = requiredInt(body, "branchId");
        int createdBy = requiredInt(body, "createdBy");
        requireSupplier(supplierId);
        requireBranch(branchId);
        requireUser(createdBy);
        Long purchaseOrderId = longOptional(body, "purchaseOrderId");
        if (purchaseOrderId != null) {
            try {
                PurchaseRepository.PurchaseRow po = repository.getPurchaseOrder(purchaseOrderId);
                if (po == null) throw notFound("Purchase order not found");
                if (po.supplierId != supplierId) throw badRequest("Receipt supplier must match purchase order supplier");
            } catch (SQLException e) {
                throw sqlError(e);
            }
        }
        JsonNode details = body.get("details");
        if (details == null || !details.isArray() || details.isEmpty()) {
            throw badRequest("Purchase receipt details must not be empty");
        }
        List<PurchaseRepository.ReceiptDetail> parsed = parseReceiptDetails(details);
        Timestamp date = body.has("receiptDate") ? parseTimestamp(body.get("receiptDate").asText(), "receiptDate") : new Timestamp(System.currentTimeMillis());
        try {
            return repository.createReceipt(uniqueCode(body, "receiptCode", "PN"), purchaseOrderId,
                    supplierId, branchId, createdBy, date, text(body, "note"), parsed);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public PurchaseRepository.ReceiptRow getReceipt(long id) {
        try {
            PurchaseRepository.ReceiptRow row = repository.getReceipt(id);
            if (row == null) throw notFound("Purchase receipt not found");
            return row;
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public void updateReceipt(long id, JsonNode body) {
        PurchaseRepository.ReceiptRow row = getReceipt(id);
        if (!"DRAFT".equals(row.status)) throw conflict("Only DRAFT purchase receipt can be updated");
        requireBody(body);
        Long poId = longOptional(body, "purchaseOrderId");
        Integer supplierId = intOptional(body, "supplierId");
        Integer branchId = intOptional(body, "branchId");
        if (supplierId != null) requireSupplier(supplierId);
        if (branchId != null) requireBranch(branchId);
        Long effectivePoId = poId == null ? row.purchaseOrderId : poId;
        int effectiveSupplierId = supplierId == null ? row.supplierId : supplierId;
        if (effectivePoId != null) {
            try {
                PurchaseRepository.PurchaseRow order = repository.getPurchaseOrder(effectivePoId);
                if (order == null) throw notFound("Purchase order not found");
                if (order.supplierId != effectiveSupplierId) {
                    throw badRequest("Receipt supplier must match purchase order supplier");
                }
            } catch (SQLException e) {
                throw sqlError(e);
            }
        }
        Timestamp date = body.has("receiptDate") ? parseTimestamp(body.get("receiptDate").asText(), "receiptDate") : null;
        try {
            repository.updateReceipt(id, poId, supplierId, branchId, date, text(body, "note"));
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public List<PurchaseRepository.ReceiptRow> searchReceipts(String fromDate, String toDate,
                                                              Integer supplierId, String status) {
        Timestamp from = fromDate == null ? null : parseTimestamp(fromDate, "fromDate");
        Timestamp to = null;
        if (toDate != null) {
            if (toDate.length() == 10) {
                to = Timestamp.valueOf(parseDate(toDate, "toDate").plusDays(1).atStartOfDay().minusSeconds(1));
            } else {
                to = parseTimestamp(toDate, "toDate");
            }
        }
        if (from != null && to != null && from.after(to)) {
            throw badRequest("fromDate must be before or equal to toDate");
        }
        if (status != null && !List.of("DRAFT", "SUBMITTED", "APPROVED", "CANCELLED").contains(status)) {
            throw badRequest("Invalid receipt status");
        }
        try {
            return repository.searchReceipts(from, to, supplierId, status);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public List<PurchaseRepository.ReceiptDetailRow> getReceiptDetails(long receiptId) {
        getReceipt(receiptId);
        try {
            return repository.getReceiptDetails(receiptId);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public long addReceiptDetail(long receiptId, JsonNode body) {
        PurchaseRepository.ReceiptRow receipt = getReceipt(receiptId);
        if (!"DRAFT".equals(receipt.status)) throw conflict("Receipt details can only be added while DRAFT");
        requireBody(body);
        PurchaseRepository.ReceiptDetail d = parseReceiptDetail(body);
        try {
            return repository.addReceiptDetail(receiptId, d);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public void updateReceiptDetail(long receiptId, long detailId, JsonNode body) {
        PurchaseRepository.ReceiptRow receipt = getReceipt(receiptId);
        if (!"DRAFT".equals(receipt.status)) throw conflict("Receipt details can only be updated while DRAFT");
        requireBody(body);
        try {
            if (!repository.getReceiptDetailOwnership(receiptId, detailId)) throw notFound("Receipt detail not found");
        } catch (SQLException e) {
            throw sqlError(e);
        }
        PurchaseRepository.ReceiptDetail d = parseReceiptDetail(body);
        try {
            repository.updateReceiptDetail(detailId, d);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public void deleteReceiptDetail(long receiptId, long detailId) {
        PurchaseRepository.ReceiptRow receipt = getReceipt(receiptId);
        if (!"DRAFT".equals(receipt.status)) throw conflict("Receipt details can only be deleted while DRAFT");
        try {
            if (!repository.getReceiptDetailOwnership(receiptId, detailId)) throw notFound("Receipt detail not found");
            if (!repository.deleteReceiptDetail(detailId)) throw notFound("Receipt detail not found");
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public void transitionReceipt(long receiptId, JsonNode body) {
        PurchaseRepository.ReceiptRow receipt = getReceipt(receiptId);
        requireBody(body);
        String status = requiredText(body, "status");
        if (!List.of("SUBMITTED", "APPROVED", "CANCELLED").contains(status)) {
            throw badRequest("Invalid receipt status");
        }
        Integer approvedBy = intOptional(body, "approvedBy");
        if ("APPROVED".equals(status) && approvedBy == null) approvedBy = receipt.createdBy;
        if (approvedBy != null) requireUser(approvedBy);
        try {
            if ("SUBMITTED".equals(status) || "APPROVED".equals(status)) {
                validateDetailsBeforeSubmit(receiptId);
            }
            if (!repository.transitionReceipt(receiptId, status, approvedBy)) throw notFound("Purchase receipt not found");
        } catch (IllegalArgumentException e) {
            throw conflict(e.getMessage());
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    private void validateDetailsBeforeSubmit(long receiptId) {
        try {
            List<PurchaseRepository.ReceiptDetailRow> details = repository.getReceiptDetails(receiptId);
            if (details.isEmpty()) throw badRequest("Purchase receipt must contain at least one detail");
            for (PurchaseRepository.ReceiptDetailRow d : validateAndNormalizeRows(details)) {
                if (d.expiryDate == null || d.expiryDate.toLocalDate().isBefore(LocalDate.now())) {
                    throw badRequest("Expiry date must be in the future");
                }
            }
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    private List<PurchaseRepository.ReceiptDetailRow> validateAndNormalizeRows(
            List<PurchaseRepository.ReceiptDetailRow> details) {
        return details;
    }

    private PurchaseRepository.OrderDetail parsePurchaseOrderDetail(JsonNode body) {
        requireBody(body);
        PurchaseRepository.OrderDetail detail = new PurchaseRepository.OrderDetail();
        detail.drugId = requiredInt(body, "drugId");
        detail.quantity = requiredDecimal(body, "quantity");
        detail.unitCost = requiredDecimal(body, "expectedUnitCost");
        requireDrug(detail.drugId);
        requirePositive(detail.quantity, "Quantity");
        requireNonNegative(detail.unitCost, "Expected unit cost");
        return detail;
    }

    public long createSupplierReturn(JsonNode body) {
        requireBody(body);
        int supplierId = requiredInt(body, "supplierId");
        int branchId = requiredInt(body, "branchId");
        int createdBy = requiredInt(body, "createdBy");
        requireSupplier(supplierId);
        requireBranch(branchId);
        requireUser(createdBy);
        JsonNode details = body.get("details");
        if (details == null || !details.isArray() || details.isEmpty()) {
            throw badRequest("Supplier return details must not be empty");
        }
        List<PurchaseRepository.ReturnDetail> parsed = new ArrayList<>();
        for (JsonNode detail : details) {
            PurchaseRepository.ReturnDetail d = new PurchaseRepository.ReturnDetail();
            d.lotId = requiredLong(detail, "lotId");
            d.quantity = requiredDecimal(detail, "quantity");
            d.unitCost = requiredDecimal(detail, "unitCost");
            requirePositive(d.quantity, "Quantity");
            requireNonNegative(d.unitCost, "Unit cost");
            try {
                if (!repository.lotExists(d.lotId)) throw notFound("Lot not found");
                if (!repository.lotBelongsToSupplier(d.lotId, supplierId, branchId)) {
                    throw badRequest("Lot does not belong to this supplier and branch");
                }
            } catch (SQLException e) {
                throw sqlError(e);
            }
            parsed.add(d);
        }
        Timestamp date = body.has("returnDate") ? parseTimestamp(body.get("returnDate").asText(), "returnDate") : new Timestamp(System.currentTimeMillis());
        try {
            return repository.createSupplierReturn(uniqueCode(body, "returnCode", "TR"), supplierId, branchId,
                    createdBy, date, text(body, "reason"), parsed);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public PurchaseRepository.ReturnRow getSupplierReturn(long id) {
        try {
            PurchaseRepository.ReturnRow row = repository.getSupplierReturn(id);
            if (row == null) throw notFound("Supplier return not found");
            return row;
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public List<PurchaseRepository.ReturnRow> listSupplierReturns(Integer supplierId, String status) {
        try {
            return repository.listSupplierReturns(supplierId, status);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public List<PurchaseRepository.ReturnDetailRow> getSupplierReturnDetails(long id) {
        getSupplierReturn(id);
        try {
            return repository.getSupplierReturnDetails(id);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public void confirmSupplierReturn(long id, JsonNode body) {
        PurchaseRepository.ReturnRow row = getSupplierReturn(id);
        int createdBy = body != null && body.has("createdBy")
                ? body.get("createdBy").asInt() : row.createdBy;
        requireUser(createdBy);
        try {
            if (!repository.confirmSupplierReturn(id, createdBy)) throw notFound("Supplier return not found");
        } catch (IllegalArgumentException e) {
            throw conflict(e.getMessage());
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public void cancelSupplierReturn(long id) {
        getSupplierReturn(id);
        try {
            if (!repository.cancelSupplierReturn(id)) throw conflict("Only DRAFT supplier return can be cancelled");
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public long createSupplierPayment(JsonNode body) {
        requireBody(body);
        int supplierId = requiredInt(body, "supplierId");
        int paidBy = requiredInt(body, "paidBy");
        requireSupplier(supplierId);
        requireUser(paidBy);
        BigDecimal amount = requiredDecimal(body, "amount");
        requirePositive(amount, "Amount");
        String method = requiredText(body, "paymentMethod");
        if (!List.of("CASH", "BANK_TRANSFER").contains(method)) throw badRequest("Invalid payment method");
        try {
            BigDecimal debt = repository.getSupplierDebt(supplierId);
            if (amount.compareTo(debt) > 0) throw badRequest("Payment amount exceeds outstanding debt");
        } catch (SQLException e) {
            throw sqlError(e);
        }
        Timestamp date = body.has("paymentDate") ? parseTimestamp(body.get("paymentDate").asText(), "paymentDate") : new Timestamp(System.currentTimeMillis());
        Integer branchId = intOptional(body, "branchId");
        if (branchId != null) requireBranch(branchId);
        try {
            return repository.createSupplierPayment(uniqueCode(body, "paymentCode", "TT"),
                    supplierId, branchId, paidBy, date, amount, method, text(body, "note"));
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public PurchaseRepository.PaymentRow getSupplierPayment(long id) {
        try {
            PurchaseRepository.PaymentRow row = repository.getSupplierPayment(id);
            if (row == null) throw notFound("Supplier payment not found");
            return row;
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public List<PurchaseRepository.PaymentRow> listSupplierPayments(Integer supplierId) {
        if (supplierId != null) requireSupplier(supplierId);
        try {
            return repository.listSupplierPayments(supplierId);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    public BigDecimal getSupplierDebt(int supplierId) {
        requireSupplier(supplierId);
        try {
            return repository.getSupplierDebt(supplierId);
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    private List<PurchaseRepository.ReceiptDetail> parseReceiptDetails(JsonNode details) {
        List<PurchaseRepository.ReceiptDetail> list = new ArrayList<>();
        for (JsonNode detail : details) list.add(parseReceiptDetail(detail));
        return list;
    }

    private PurchaseRepository.ReceiptDetail parseReceiptDetail(JsonNode detail) {
        requireBody(detail);
        PurchaseRepository.ReceiptDetail d = new PurchaseRepository.ReceiptDetail();
        d.drugId = requiredInt(detail, "drugId");
        d.lotNumber = requiredText(detail, "lotNumber");
        d.quantity = requiredDecimal(detail, "quantity");
        d.unitCost = requiredDecimal(detail, "unitCost");
        d.expiryDate = parseDate(requiredText(detail, "expiryDate"), "expiryDate");
        d.manufactureDate = detail.has("manufactureDate") && !detail.get("manufactureDate").isNull()
                ? parseDate(detail.get("manufactureDate").asText(), "manufactureDate") : null;
        requireDrug(d.drugId);
        requirePositive(d.quantity, "Quantity");
        requireNonNegative(d.unitCost, "Unit cost");
        if (d.lotNumber.isBlank()) throw badRequest("Lot number must not be empty");
        if (!d.expiryDate.isAfter(LocalDate.now())) throw badRequest("Expiry date must be in the future");
        if (d.manufactureDate != null && !d.expiryDate.isAfter(d.manufactureDate)) {
            throw badRequest("Expiry date must be after manufacture date");
        }
        return d;
    }

    private void requireSupplier(int id) {
        try {
            if (!repository.supplierExists(id)) throw notFound("Supplier not found");
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    private void requireBranch(int id) {
        try {
            if (!repository.branchExists(id)) throw notFound("Branch not found");
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    private void requireUser(int id) {
        try {
            if (!repository.userExists(id)) throw notFound("User not found");
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    private void requireDrug(int id) {
        try {
            if (!repository.drugExists(id)) throw notFound("Product not found");
        } catch (SQLException e) {
            throw sqlError(e);
        }
    }

    private void requireBody(JsonNode body) {
        if (body == null || body.isNull()) throw badRequest("Missing JSON body");
    }

    private void requirePositive(BigDecimal value, String name) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) throw badRequest(name + " must be greater than 0");
    }

    private void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) throw badRequest(name + " must not be negative");
    }

    private int requiredInt(JsonNode body, String field) {
        if (body == null || !body.has(field) || body.get(field).isNull()) throw badRequest(field + " is required");
        JsonNode value = body.get(field);
        if (!value.canConvertToInt()) throw badRequest(field + " must be an integer");
        return value.asInt();
    }

    private Integer intOptional(JsonNode body, String field) {
        if (body == null || !body.has(field) || body.get(field).isNull()) return null;
        if (!body.get(field).canConvertToInt()) throw badRequest(field + " must be an integer");
        return body.get(field).asInt();
    }

    private Long longOptional(JsonNode body, String field) {
        if (body == null || !body.has(field) || body.get(field).isNull()) return null;
        if (!body.get(field).canConvertToLong()) throw badRequest(field + " must be a long integer");
        return body.get(field).asLong();
    }

    private long requiredLong(JsonNode body, String field) {
        if (body == null || !body.has(field) || body.get(field).isNull()) throw badRequest(field + " is required");
        if (!body.get(field).canConvertToLong()) throw badRequest(field + " must be a long integer");
        return body.get(field).asLong();
    }

    private BigDecimal requiredDecimal(JsonNode body, String field) {
        if (body == null || !body.has(field) || body.get(field).isNull()) throw badRequest(field + " is required");
        try {
            return body.get(field).decimalValue();
        } catch (RuntimeException e) {
            throw badRequest(field + " must be a decimal number");
        }
    }

    private String requiredText(JsonNode body, String field) {
        if (body == null || !body.has(field) || body.get(field).isNull()) throw badRequest(field + " is required");
        String value = body.get(field).asText();
        if (value == null || value.isBlank()) throw badRequest(field + " must not be empty");
        return value;
    }

    private String text(JsonNode body, String field) {
        if (body == null || !body.has(field) || body.get(field).isNull()) return null;
        return body.get(field).asText();
    }

    private String uniqueCode(JsonNode body, String field, String prefix) {
        String code = text(body, field);
        return code == null || code.isBlank()
                ? prefix + "-" + System.currentTimeMillis()
                : code;
    }

    private Timestamp parseTimestamp(String value, String field) {
        try {
            if (value.length() == 10) {
                return Timestamp.valueOf(java.time.LocalDate.parse(value).atStartOfDay());
            }
            return Timestamp.valueOf(java.time.LocalDateTime.parse(value.replace('Z', ' ').trim()));
        } catch (RuntimeException e) {
            throw badRequest(field + " must use ISO date or date-time format");
        }
    }

    private LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw badRequest(field + " must use YYYY-MM-DD format");
        }
    }

    private PurchaseException badRequest(String message) {
        return new PurchaseException(400, message);
    }

    private PurchaseException notFound(String message) {
        return new PurchaseException(404, message);
    }

    private PurchaseException conflict(String message) {
        return new PurchaseException(409, message);
    }

    private PurchaseException sqlError(SQLException e) {
        if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
            return new PurchaseException(409, "Duplicate or invalid data");
        }
        return new PurchaseException(500, "Database operation failed");
    }
}

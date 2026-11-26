package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import exception.PurchaseException;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import repository.PurchaseRepository;
import service.PurchaseService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.util.List;

@Singleton
public class PurchaseController extends Controller {
    private final PurchaseService service;

    @Inject
    public PurchaseController(PurchaseService service) {
        this.service = service;
    }

    public Result createPurchaseOrder(Http.Request request) {
        return execute(() -> okJson(purchaseOrderCreated(service.createPurchaseOrder(request.body().asJson()))));
    }

    public Result listPurchaseOrders(Http.Request request) {
        return execute(() -> {
            String keyword = queryString(request, "keyword");
            Integer supplierId = queryInt(request, "supplierId");
            String status = queryString(request, "status");
            List<PurchaseRepository.PurchaseRow> rows = service.listOrders(keyword, supplierId, status);
            ArrayNode data = Json.newArray();
            for (PurchaseRepository.PurchaseRow row : rows) data.add(purchaseOrderJson(row));
            return ok((JsonNode) Json.newObject().put("success", true).set("data", data));
        });
    }

    public Result getPurchaseOrder(long id) {
        return execute(() -> ok((JsonNode) Json.newObject().put("success", true).set("data", purchaseOrderJson(service.getPurchaseOrder(id)))));
    }

    public Result getPurchaseOrderDetails(long id) {
        return execute(() -> {
            ArrayNode data = Json.newArray();
            for (PurchaseRepository.DetailRow row : service.getPurchaseOrderDetails(id)) data.add(orderDetailJson(row));
            return ok((JsonNode) Json.newObject().put("success", true).set("data", data));
        });
    }

    public Result addPurchaseOrderDetail(Http.Request request, long id) {
        return execute(() -> {
            long detailId = service.addPurchaseOrderDetail(id, request.body().asJson());
            return ok((JsonNode) Json.newObject().put("success", true).set("data",
                    Json.newObject().put("purchaseOrderId", id).put("purchaseOrderDetailId", detailId)));
        });
    }

    public Result updatePurchaseOrderDetail(Http.Request request, long id, long detailId) {
        return execute(() -> {
            service.updatePurchaseOrderDetail(id, detailId, request.body().asJson());
            return ok((JsonNode) Json.newObject().put("success", true).put("message", "Purchase order detail updated"));
        });
    }

    public Result deletePurchaseOrderDetail(long id, long detailId) {
        return execute(() -> {
            service.deletePurchaseOrderDetail(id, detailId);
            return ok((JsonNode) Json.newObject().put("success", true).put("message", "Purchase order detail deleted"));
        });
    }

    public Result updatePurchaseOrder(Http.Request request, long id) {
        return execute(() -> {
            service.updatePurchaseOrder(id, request.body().asJson());
            return ok((JsonNode) Json.newObject().put("success", true).set("data", purchaseOrderJson(service.getPurchaseOrder(id))));
        });
    }

    public Result cancelPurchaseOrder(long id) {
        return execute(() -> {
            service.cancelPurchaseOrder(id);
            return ok((JsonNode) Json.newObject().put("success", true).put("message", "Purchase order cancelled"));
        });
    }

    public Result createReceipt(Http.Request request) {
        return execute(() -> okJson(receiptCreated(service.createReceipt(request.body().asJson()))));
    }

    public Result listReceipts(Http.Request request) {
        return execute(() -> {
            String fromDate = queryString(request, "fromDate");
            String toDate = queryString(request, "toDate");
            Integer supplierId = queryInt(request, "supplierId");
            String status = queryString(request, "status");
            ArrayNode data = Json.newArray();
            for (PurchaseRepository.ReceiptRow row : service.searchReceipts(fromDate, toDate, supplierId, status)) {
                data.add(receiptJson(row));
            }
            return ok((JsonNode) Json.newObject().put("success", true).set("data", data));
        });
    }

    public Result getReceipt(long id) {
        return execute(() -> {
            PurchaseRepository.ReceiptRow row = service.getReceipt(id);
            ObjectNode data = receiptJson(row);
            data.set("details", receiptDetailsArray(id));
            return ok((JsonNode) Json.newObject().put("success", true).set("data", data));
        });
    }

    public Result updateReceipt(Http.Request request, long id) {
        return execute(() -> {
            service.updateReceipt(id, request.body().asJson());
            return ok((JsonNode) Json.newObject().put("success", true).set("data", receiptJson(service.getReceipt(id))));
        });
    }

    public Result getReceiptDetails(long receiptId) {
        return execute(() -> ok((JsonNode) Json.newObject().put("success", true).set("data", receiptDetailsArray(receiptId))));
    }

    public Result addReceiptDetail(Http.Request request, long receiptId) {
        return execute(() -> okJson(receiptDetailCreated(receiptId,
                service.addReceiptDetail(receiptId, request.body().asJson()))));
    }

    public Result updateReceiptDetail(Http.Request request, long receiptId, long detailId) {
        return execute(() -> {
            service.updateReceiptDetail(receiptId, detailId, request.body().asJson());
            return ok((JsonNode) Json.newObject().put("success", true).put("message", "Receipt detail updated"));
        });
    }

    public Result deleteReceiptDetail(long receiptId, long detailId) {
        return execute(() -> {
            service.deleteReceiptDetail(receiptId, detailId);
            return ok((JsonNode) Json.newObject().put("success", true).put("message", "Receipt detail deleted"));
        });
    }

    public Result transitionReceipt(Http.Request request, long id) {
        return execute(() -> {
            service.transitionReceipt(id, request.body().asJson());
            return ok((JsonNode) Json.newObject().put("success", true).set("data", receiptJson(service.getReceipt(id))));
        });
    }

    public Result createSupplierReturn(Http.Request request) {
        return execute(() -> okJson(supplierReturnCreated(service.createSupplierReturn(request.body().asJson()))));
    }

    public Result listSupplierReturns(Http.Request request) {
        return execute(() -> {
            Integer supplierId = queryInt(request, "supplierId");
            String status = queryString(request, "status");
            ArrayNode data = Json.newArray();
            for (PurchaseRepository.ReturnRow row : service.listSupplierReturns(supplierId, status)) data.add(supplierReturnJson(row));
            return ok((JsonNode) Json.newObject().put("success", true).set("data", data));
        });
    }

    public Result getSupplierReturn(long id) {
        return execute(() -> ok((JsonNode) Json.newObject().put("success", true).set("data", supplierReturnJson(service.getSupplierReturn(id)))));
    }

    public Result getSupplierReturnDetails(long id) {
        return execute(() -> {
            ArrayNode data = Json.newArray();
            for (PurchaseRepository.ReturnDetailRow row : service.getSupplierReturnDetails(id)) {
                data.add(supplierReturnDetailJson(row));
            }
            return ok((JsonNode) Json.newObject().put("success", true).set("data", data));
        });
    }

    public Result confirmSupplierReturn(Http.Request request, long id) {
        return execute(() -> {
            service.confirmSupplierReturn(id, request.body().asJson());
            return ok((JsonNode) Json.newObject().put("success", true).set("data", supplierReturnJson(service.getSupplierReturn(id))));
        });
    }

    public Result cancelSupplierReturn(long id) {
        return execute(() -> {
            service.cancelSupplierReturn(id);
            return ok((JsonNode) Json.newObject().put("success", true).put("message", "Supplier return cancelled"));
        });
    }

    public Result createSupplierPayment(Http.Request request) {
        return execute(() -> okJson(supplierPaymentCreated(service.createSupplierPayment(request.body().asJson()))));
    }

    public Result listSupplierPayments(Http.Request request) {
        return execute(() -> {
            Integer supplierId = queryInt(request, "supplierId");
            ArrayNode data = Json.newArray();
            for (PurchaseRepository.PaymentRow row : service.listSupplierPayments(supplierId)) data.add(paymentJson(row));
            return ok((JsonNode) Json.newObject().put("success", true).set("data", data));
        });
    }

    public Result getSupplierPayment(long id) {
        return execute(() -> ok((JsonNode) Json.newObject().put("success", true).set("data", paymentJson(service.getSupplierPayment(id)))));
    }

    public Result getSupplierDebt(int supplierId) {
        return execute(() -> ok((JsonNode) Json.newObject().put("success", true)
                .put("supplierId", supplierId).set("debt", Json.mapper().valueToTree(service.getSupplierDebt(supplierId)))));
    }

    private ArrayNode receiptDetailsArray(long receiptId) {
        ArrayNode data = Json.newArray();
        for (PurchaseRepository.ReceiptDetailRow row : service.getReceiptDetails(receiptId)) data.add(receiptDetailJson(row));
        return data;
    }

    private ObjectNode purchaseOrderCreated(long id) {
        return Json.newObject().put("purchaseOrderId", id);
    }

    private ObjectNode receiptCreated(long id) {
        return Json.newObject().put("purchaseReceiptId", id);
    }

    private ObjectNode receiptDetailCreated(long receiptId, long detailId) {
        return Json.newObject().put("purchaseReceiptId", receiptId).put("purchaseReceiptDetailId", detailId);
    }

    private ObjectNode supplierReturnCreated(long id) {
        return Json.newObject().put("supplierReturnId", id);
    }

    private ObjectNode supplierPaymentCreated(long id) {
        return Json.newObject().put("supplierPaymentId", id);
    }

    private ObjectNode purchaseOrderJson(PurchaseRepository.PurchaseRow row) {
        return Json.newObject().put("purchaseOrderId", row.id).put("orderCode", row.code)
                .put("supplierId", row.supplierId).put("branchId", row.branchId)
                .put("createdBy", row.createdBy).put("orderDate", row.orderDate.toInstant().toString())
                .put("status", row.status).put("note", row.note);
    }

    private ObjectNode orderDetailJson(PurchaseRepository.DetailRow row) {
        return Json.newObject().put("purchaseOrderDetailId", row.id).put("purchaseOrderId", row.purchaseOrderId)
                .put("drugId", row.drugId).put("quantity", row.quantity).put("expectedUnitCost", row.unitCost);
    }

    private ObjectNode supplierReturnDetailJson(PurchaseRepository.ReturnDetailRow row) {
        return Json.newObject().put("supplierReturnDetailId", row.id).put("supplierReturnId", row.returnId)
                .put("lotId", row.lotId).put("quantity", row.quantity).put("unitCost", row.unitCost);
    }

    private ObjectNode receiptJson(PurchaseRepository.ReceiptRow row) {
        ObjectNode json = Json.newObject().put("purchaseReceiptId", row.id).put("receiptCode", row.code);
        if (row.purchaseOrderId != null) json.put("purchaseOrderId", row.purchaseOrderId);
        if (row.approvedBy != null) json.put("approvedBy", row.approvedBy);
        return json.put("supplierId", row.supplierId).put("branchId", row.branchId)
                .put("createdBy", row.createdBy).put("receiptDate", row.receiptDate.toInstant().toString())
                .put("status", row.status).put("totalAmount", row.totalAmount).put("note", row.note);
    }

    private ObjectNode receiptDetailJson(PurchaseRepository.ReceiptDetailRow row) {
        return Json.newObject().put("purchaseReceiptDetailId", row.id).put("purchaseReceiptId", row.receiptId)
                .put("drugId", row.drugId).put("lotNumber", row.lotNumber)
                .put("expiryDate", row.expiryDate.toLocalDate().toString())
                .put("manufactureDate", row.manufactureDate == null ? null : row.manufactureDate.toLocalDate().toString())
                .put("quantity", row.quantity).put("unitCost", row.unitCost);
    }

    private ObjectNode supplierReturnJson(PurchaseRepository.ReturnRow row) {
        return Json.newObject().put("supplierReturnId", row.id).put("returnCode", row.code)
                .put("supplierId", row.supplierId).put("branchId", row.branchId).put("createdBy", row.createdBy)
                .put("returnDate", row.returnDate.toInstant().toString()).put("status", row.status).put("reason", row.reason);
    }

    private ObjectNode paymentJson(PurchaseRepository.PaymentRow row) {
        ObjectNode json = Json.newObject().put("supplierPaymentId", row.id).put("paymentCode", row.code)
                .put("supplierId", row.supplierId).put("paidBy", row.paidBy)
                .put("paymentDate", row.paymentDate.toInstant().toString())
                .put("amount", row.amount).put("paymentMethod", row.paymentMethod).put("note", row.note);
        if (row.branchId != null) json.put("branchId", row.branchId);
        return json;
    }

    private Result okJson(ObjectNode data) {
        return ok((JsonNode) Json.newObject().put("success", true).set("data", data));
    }

    private String queryString(Http.Request request, String name) {
        return request.queryString(name).orElse(null);
    }

    private Integer queryInt(Http.Request request, String name) {
        try {
            String value = queryString(request, name);
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new PurchaseException(400, name + " must be an integer");
        }
    }

    private Result execute(CheckedSupplier<Result> action) {
        try {
            return action.get();
        } catch (PurchaseException e) {
            ObjectNode error = Json.newObject().put("success", false).put("error", e.getMessage());
            return status(e.getStatus(), error);
        } catch (RuntimeException e) {
            ObjectNode error = Json.newObject().put("success", false).put("error", "Unexpected server error");
            return internalServerError(error);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get();
    }
}

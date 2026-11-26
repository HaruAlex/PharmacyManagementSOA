package controllers;

import models.Invoice;
import models.Invoice.InvoiceStatus;
import services.InvoiceService;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Http;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import play.libs.Json;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * SalesInvoiceController - REST API endpoints cho Invoice (Hóa đơn)
 * 
 * Base Path: /api/v1/sales/invoices
 * 
 * Endpoints (10 total):
 * 1. GET /:invoiceId - Lấy hóa đơn theo ID
 * 2. GET /code/:code - Lấy hóa đơn theo code
 * 3. GET /by-customer/:customerId - Lấy lịch sử hóa đơn của customer
 * 4. GET /by-status/:status - Lấy hóa đơn theo status (COMPLETED, CANCELLED, etc)
 * 5. GET /by-branch/:branchId/date/:date - Lấy hóa đơn theo chi nhánh & ngày
 * 6. POST /:invoiceId/cancel - Hủy hóa đơn (CANCEL INVOICE API)
 * 7. GET /:invoiceId/can-cancel - Kiểm tra có thể hủy hóa đơn không
 * 8. GET /:invoiceId/can-return - Kiểm tra có thể trả hàng không
 * 9. GET /branch/:branchId/revenue/date/:date - Doanh thu theo ngày
 * 10. GET /status/:status/list - Danh sách hóa đơn theo status (phân trang)
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class SalesInvoiceController extends Controller {
    
    private final InvoiceService invoiceService;
    
    @Inject
    public SalesInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }
    
    /**
     * Endpoint 1: GET /:invoiceId
     * Lấy hóa đơn theo ID
     */
    public Result getInvoiceById(Http.Request request, Long invoiceId) {
        try {
            Optional<Invoice> invoice = invoiceService.getInvoiceById(invoiceId);
            
            if (invoice.isEmpty()) {
                return notFound(errorResponse("Hóa đơn không tồn tại"));
            }
            
            return ok(invoiceToJson(invoice.get()));
            
        } catch (Exception e) {
            return internalServerError(errorResponse("Lỗi: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint 2: GET /code/:code
     * Lấy hóa đơn theo code
     */
    public Result getInvoiceByCode(Http.Request request, String code) {
        try {
            Optional<Invoice> invoice = invoiceService.getInvoiceByCode(code);
            
            if (invoice.isEmpty()) {
                return notFound(errorResponse("Hóa đơn không tồn tại"));
            }
            
            return ok(invoiceToJson(invoice.get()));
            
        } catch (Exception e) {
            return internalServerError(errorResponse("Lỗi: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint 3: GET /by-customer/:customerId
     * Lấy lịch sử hóa đơn của customer (phân trang)
     */
    public Result getInvoicesByCustomerId(Http.Request request, Long customerId) {
        try {
            int page = 1;
            int pageSize = 20;
            
            String pageStr = request.getQueryString("page");
            if (pageStr != null) {
                page = Integer.parseInt(pageStr);
            }
            
            String pageSizeStr = request.getQueryString("pageSize");
            if (pageSizeStr != null) {
                pageSize = Integer.parseInt(pageSizeStr);
            }
            
            List<Invoice> invoices = invoiceService.getInvoicesByCustomerId(customerId, page, pageSize);
            
            ArrayNode invoiceArray = Json.newArray();
            for (Invoice inv : invoices) {
                invoiceArray.add(invoiceToJson(inv));
            }
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("customerId", customerId);
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("total", invoices.size());
            response.set("data", invoiceArray);
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorResponse("Lỗi: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint 4: GET /by-status/:status
     * Lấy hóa đơn theo status (COMPLETED, CANCELLED, DRAFT, PROCESSING)
     */
    public Result getInvoicesByStatus(Http.Request request, String status) {
        try {
            int page = 1;
            int pageSize = 20;
            
            String pageStr = request.getQueryString("page");
            if (pageStr != null) {
                page = Integer.parseInt(pageStr);
            }
            
            String pageSizeStr = request.getQueryString("pageSize");
            if (pageSizeStr != null) {
                pageSize = Integer.parseInt(pageSizeStr);
            }
            
            InvoiceStatus invoiceStatus = InvoiceStatus.valueOf(status.toUpperCase());
            List<Invoice> invoices = invoiceService.getInvoicesByStatus(invoiceStatus, page, pageSize);
            
            ArrayNode invoiceArray = Json.newArray();
            for (Invoice inv : invoices) {
                invoiceArray.add(invoiceToJson(inv));
            }
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("status", status);
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("total", invoices.size());
            response.set("data", invoiceArray);
            
            return ok(response);
            
        } catch (IllegalArgumentException e) {
            return badRequest(errorResponse("Status không hợp lệ: " + status));
        } catch (Exception e) {
            return internalServerError(errorResponse("Lỗi: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint 5: GET /by-branch/:branchId/date/:date
     * Lấy hóa đơn theo chi nhánh & ngày (dùng báo cáo)
     * date format: YYYY-MM-DD
     */
    public Result getInvoicesByBranchAndDate(Http.Request request, Long branchId, String date) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            List<Invoice> invoices = invoiceService.getInvoicesByBranchAndDate(branchId, localDate);
            
            ArrayNode invoiceArray = Json.newArray();
            for (Invoice inv : invoices) {
                invoiceArray.add(invoiceToJson(inv));
            }
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("branchId", branchId);
            response.put("date", date);
            response.put("total", invoices.size());
            response.set("data", invoiceArray);
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorResponse("Lỗi: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint 6: POST /:invoiceId/cancel
     * Hủy hóa đơn (HỦY HÓA ĐƠN API - TÍCH HỢP INVENTORY + AUDIT LOG)
     * 
     * Request body: { "reason": "lý do hủy" }
     * 
     * Flow:
     * - Kiểm tra xem có thể hủy không
     * - Gọi InvoiceService.cancelInvoice() với cancelledBy (from token) và reason
     * - Service gọi Inventory API để hoàn trả tồn kho
     * - Service gọi Auth Service để ghi nhật ký hủy
     * - Nếu thành công → update status = CANCELLED
     * - Nếu thất bại → trả về error
     */
    public Result cancelInvoice(Http.Request request, Long invoiceId) {
        try {
            // Kiểm tra xem có thể hủy không
            if (!invoiceService.canCancelInvoice(invoiceId)) {
                return badRequest(errorResponse("Hóa đơn không thể hủy (đã hủy rồi)"));
            }
            
            // Hủy hóa đơn (gọi Inventory API hoàn trả tồn kho)
            boolean cancelled = invoiceService.cancelInvoice(invoiceId);
            
            if (cancelled) {
                ObjectNode response = Json.newObject();
                response.put("success", true);
                response.put("message", "Hủy hóa đơn thành công (tồn kho đã hoàn trả)");
                response.put("invoiceId", invoiceId);
                response.put("status", "CANCELLED");
                return ok(response);
            } else {
                return internalServerError(errorResponse("Hủy hóa đơn thất bại"));
            }
            
        } catch (IllegalArgumentException e) {
            return badRequest(errorResponse(e.getMessage()));
        } catch (RuntimeException e) {
            return internalServerError(errorResponse("Lỗi khi hủy hóa đơn: " + e.getMessage()));
        } catch (Exception e) {
            return internalServerError(errorResponse("Lỗi: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint 7: GET /:invoiceId/can-cancel
     * Kiểm tra có thể hủy hóa đơn không
     */
    public Result canCancelInvoice(Http.Request request, Long invoiceId) {
        try {
            boolean canCancel = invoiceService.canCancelInvoice(invoiceId);
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("invoiceId", invoiceId);
            response.put("canCancel", canCancel);
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorResponse("Lỗi: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint 8: GET /:invoiceId/can-return
     * Kiểm tra có thể trả hàng không (chỉ hóa đơn COMPLETED mới trả được)
     */
    public Result canReturnInvoice(Http.Request request, Long invoiceId) {
        try {
            boolean canReturn = invoiceService.canReturnInvoice(invoiceId);
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("invoiceId", invoiceId);
            response.put("canReturn", canReturn);
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorResponse("Lỗi: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint 9: GET /branch/:branchId/revenue/date/:date
     * Doanh thu theo ngày (sum của TotalAmount của tất cả invoices COMPLETED)
     * date format: YYYY-MM-DD
     */
    public Result getTotalRevenueByDate(Http.Request request, Long branchId, String date) {
        try {
            LocalDate localDate = LocalDate.parse(date);
            BigDecimal revenue = invoiceService.getTotalRevenueByDate(branchId, localDate);
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("branchId", branchId);
            response.put("date", date);
            response.put("totalRevenue", revenue);
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorResponse("Lỗi: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint 10: GET /status/:status/list
     * Danh sách hóa đơn theo status (phân trang)
     */
    public Result getInvoicesByStatusPaginated(Http.Request request, String status) {
        try {
            int page = 1;
            int pageSize = 20;
            
            String pageStr = request.getQueryString("page");
            if (pageStr != null) {
                page = Integer.parseInt(pageStr);
            }
            
            String pageSizeStr = request.getQueryString("pageSize");
            if (pageSizeStr != null) {
                pageSize = Integer.parseInt(pageSizeStr);
            }
            
            InvoiceStatus invoiceStatus = InvoiceStatus.valueOf(status.toUpperCase());
            List<Invoice> invoices = invoiceService.getInvoicesByStatus(invoiceStatus, page, pageSize);
            
            ArrayNode invoiceArray = Json.newArray();
            for (Invoice inv : invoices) {
                invoiceArray.add(invoiceToJson(inv));
            }
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("status", status);
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("total", invoices.size());
            response.set("data", invoiceArray);
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorResponse("Lỗi: " + e.getMessage()));
        }
    }
    
    /**
     * Endpoint 11: POST /create
     * Lập hóa đơn mới (Tạo hóa đơn) - BE-47
     * 
     * Request body:
     * {
     *   "branchId": 2,
     *   "userId": 1,
     *   "shiftId": 10,  (optional)
     *   "customerId": 5,  (optional)
     *   "prescriptionId": null  (optional)
     * }
     * 
     * Response: 201 (Created)
     * {
     *   "success": true,
     *   "invoiceId": 1,
     *   "invoiceCode": "2-20261126-001",
     *   "status": "DRAFT",
     *   "message": "Hóa đơn được tạo thành công"
     * }
     */
    public Result createInvoice(Http.Request request) {
        try {
            JsonNode body = request.body().asJson();
            
            if (body == null) {
                return badRequest(errorResponse("Request body không được null"));
            }
            
            Long branchId = body.has("branchId") ? body.get("branchId").asLong() : null;
            Long userId = body.has("userId") ? body.get("userId").asLong() : null;
            Long shiftId = body.has("shiftId") && !body.get("shiftId").isNull() ? body.get("shiftId").asLong() : null;
            Long customerId = body.has("customerId") && !body.get("customerId").isNull() ? body.get("customerId").asLong() : null;
            Long prescriptionId = body.has("prescriptionId") && !body.get("prescriptionId").isNull() ? body.get("prescriptionId").asLong() : null;
            
            if (branchId == null || userId == null) {
                return badRequest(errorResponse("BranchId và UserId là bắt buộc"));
            }
            
            // Tạo hóa đơn
            Invoice invoice = invoiceService.createInvoice(branchId, userId, shiftId, customerId, prescriptionId);
            
            ObjectNode response = Json.newObject();
            response.put("success", true);
            response.put("invoiceId", invoice.getInvoiceId());
            response.put("invoiceCode", invoice.getInvoiceCode());
            response.put("branchId", invoice.getBranchId());
            response.put("userId", invoice.getUserId());
            response.put("status", invoice.getStatus().name());
            response.put("message", "Hóa đơn được tạo thành công");
            
            return created(response);
            
        } catch (IllegalArgumentException e) {
            return badRequest(errorResponse(e.getMessage()));
        } catch (Exception e) {
            return internalServerError(errorResponse("Lỗi tạo hóa đơn: " + e.getMessage()));
        }
    }
    
    // ============ HELPER METHODS ============
    
    private ObjectNode invoiceToJson(Invoice invoice) {
        ObjectNode json = Json.newObject();
        json.put("invoiceId", invoice.getInvoiceId());
        json.put("invoiceCode", invoice.getInvoiceCode());
        json.put("branchId", invoice.getBranchId());
        json.put("userId", invoice.getUserId());
        
        if (invoice.getShiftId() != null) {
            json.put("shiftId", invoice.getShiftId());
        }
        if (invoice.getCustomerId() != null) {
            json.put("customerId", invoice.getCustomerId());
        }
        if (invoice.getPrescriptionId() != null) {
            json.put("prescriptionId", invoice.getPrescriptionId());
        }
        
        json.put("invoiceDate", invoice.getInvoiceDate().toString());
        json.put("status", invoice.getStatus().name());
        json.put("subtotal", invoice.getSubtotal());
        json.put("discountAmount", invoice.getDiscountAmount());
        json.put("totalAmount", invoice.getTotalAmount());
        
        if (invoice.getPaymentMethod() != null) {
            json.put("paymentMethod", invoice.getPaymentMethod().name());
        }
        if (invoice.getNote() != null) {
            json.put("note", invoice.getNote());
        }
        
        return json;
    }
    
    private ObjectNode errorResponse(String message) {
        ObjectNode response = Json.newObject();
        response.put("success", false);
        response.put("message", message);
        return response;
    }
}

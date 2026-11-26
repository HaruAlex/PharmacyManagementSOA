package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.InvoiceDetail;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.InvoiceDetailService;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.*;

import static play.mvc.Results.*;

/**
 * SalesInvoiceDetailController - REST API cho quản lý chi tiết hóa đơn
 * 
 * Base Path: /api/v1/sales/invoice-details
 * 
 * Endpoints:
 * - POST /create - Thêm chi tiết hóa đơn mới
 * - PUT /:id - Cập nhật chi tiết hóa đơn
 * - GET /:id - Lấy chi tiết hóa đơn theo ID
 * - DELETE /:id - Xóa chi tiết hóa đơn
 * - GET /by-invoice/:invoiceId - Lấy tất cả chi tiết của một hóa đơn
 * - GET /total/:invoiceId - Lấy tổng tiền hóa đơn
 * - GET /count/:invoiceId - Lấy số lượng chi tiết hóa đơn
 * - DELETE /all/:invoiceId - Xóa tất cả chi tiết của hóa đơn
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class SalesInvoiceDetailController extends Controller {
    
    private final InvoiceDetailService invoiceDetailService;
    
    @Inject
    public SalesInvoiceDetailController(InvoiceDetailService invoiceDetailService) {
        this.invoiceDetailService = invoiceDetailService;
    }
    
    /**
     * POST /api/v1/sales/invoice-details/create
     * 
     * Thêm chi tiết hóa đơn mới
     * 
     * Request Body:
     * {
     *   "invoiceId": 100,
     *   "drugId": 1,
     *   "unitId": 1,
     *   "quantity": 2,
     *   "quantityBase": 20,
     *   "unitPrice": 50000,
     *   "discountAmount": 0
     * }
     * 
     * Response: 201 Created
     */
    public Result createInvoiceDetail(Http.Request request) {
        try {
            JsonNode json = request.body().asJson();
            
            if (json == null) {
                return badRequest("{\"error\": \"Invalid JSON\"}");
            }
            
            // Parse request
            Long invoiceId = json.has("invoiceId") ? json.get("invoiceId").asLong() : null;
            Long drugId = json.has("drugId") ? json.get("drugId").asLong() : null;
            Long unitId = json.has("unitId") ? json.get("unitId").asLong() : null;
            BigDecimal quantity = json.has("quantity") ? BigDecimal.valueOf(json.get("quantity").asDouble()) : null;
            BigDecimal quantityBase = json.has("quantityBase") ? BigDecimal.valueOf(json.get("quantityBase").asDouble()) : null;
            BigDecimal unitPrice = json.has("unitPrice") ? BigDecimal.valueOf(json.get("unitPrice").asDouble()) : null;
            BigDecimal discountAmount = json.has("discountAmount") ? BigDecimal.valueOf(json.get("discountAmount").asDouble()) : BigDecimal.ZERO;
            
            // Create InvoiceDetail
            InvoiceDetail detail = new InvoiceDetail();
            detail.setInvoiceId(invoiceId);
            detail.setDrugId(drugId);
            detail.setUnitId(unitId);
            detail.setQuantity(quantity);
            detail.setQuantityBase(quantityBase);
            detail.setUnitPrice(unitPrice);
            detail.setDiscountAmount(discountAmount);
            
            // Add to service
            InvoiceDetail created = invoiceDetailService.addInvoiceDetail(detail);
            
            return created(toJson(created)).withHeader("Content-Type", "application/json");
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * PUT /api/v1/sales/invoice-details/:id
     * 
     * Cập nhật chi tiết hóa đơn
     * 
     * Request Body:
     * {
     *   "quantity": 3,
     *   "quantityBase": 30,
     *   "unitPrice": 50000,
     *   "discountAmount": 5000
     * }
     * 
     * Response: 200 OK
     */
    public Result updateInvoiceDetail(Http.Request request, Long id) {
        try {
            JsonNode json = request.body().asJson();
            
            if (json == null) {
                return badRequest("{\"error\": \"Invalid JSON\"}");
            }
            
            // Get existing detail
            Optional<InvoiceDetail> existing = invoiceDetailService.getInvoiceDetailById(id);
            if (!existing.isPresent()) {
                return notFound("{\"error\": \"InvoiceDetail not found\"}");
            }
            
            InvoiceDetail detail = existing.get();
            
            // Update fields
            if (json.has("quantity")) {
                detail.setQuantity(BigDecimal.valueOf(json.get("quantity").asDouble()));
            }
            if (json.has("quantityBase")) {
                detail.setQuantityBase(BigDecimal.valueOf(json.get("quantityBase").asDouble()));
            }
            if (json.has("unitPrice")) {
                detail.setUnitPrice(BigDecimal.valueOf(json.get("unitPrice").asDouble()));
            }
            if (json.has("discountAmount")) {
                detail.setDiscountAmount(BigDecimal.valueOf(json.get("discountAmount").asDouble()));
            }
            
            // Update in service
            InvoiceDetail updated = invoiceDetailService.updateInvoiceDetail(detail);
            
            return ok(toJson(updated)).withHeader("Content-Type", "application/json");
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/invoice-details/:id
     * 
     * Lấy chi tiết hóa đơn theo ID
     * 
     * Response: 200 OK
     */
    public Result getInvoiceDetail(Http.Request request, Long id) {
        try {
            Optional<InvoiceDetail> detail = invoiceDetailService.getInvoiceDetailById(id);
            
            if (!detail.isPresent()) {
                return notFound("{\"error\": \"InvoiceDetail not found\"}");
            }
            
            return ok(toJson(detail.get())).withHeader("Content-Type", "application/json");
            
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * DELETE /api/v1/sales/invoice-details/:id
     * 
     * Xóa chi tiết hóa đơn
     * 
     * Response: 204 No Content
     */
    public Result deleteInvoiceDetail(Http.Request request, Long id) {
        try {
            boolean deleted = invoiceDetailService.deleteInvoiceDetail(id);
            
            if (!deleted) {
                return notFound("{\"error\": \"InvoiceDetail not found\"}");
            }
            
            return noContent();
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/invoice-details/by-invoice/:invoiceId
     * 
     * Lấy tất cả chi tiết của một hóa đơn
     * 
     * Response: 200 OK với danh sách chi tiết
     */
    public Result getInvoiceDetailsByInvoiceId(Http.Request request, Long invoiceId) {
        try {
            List<InvoiceDetail> details = invoiceDetailService.getInvoiceDetailsByInvoiceId(invoiceId);
            
            return ok(toJsonArray(details)).withHeader("Content-Type", "application/json");
            
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/invoice-details/total/:invoiceId
     * 
     * Lấy tổng tiền của hóa đơn (SUM(LineTotal))
     * 
     * Response: 200 OK với JSON
     * {
     *   "invoiceId": 100,
     *   "total": 500000.00
     * }
     */
    public Result getInvoiceTotalAmount(Http.Request request, Long invoiceId) {
        try {
            BigDecimal total = invoiceDetailService.getInvoiceTotalAmount(invoiceId);
            
            String json = "{\"invoiceId\": " + invoiceId + ", \"total\": " + total + "}";
            return ok(json).withHeader("Content-Type", "application/json");
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/invoice-details/count/:invoiceId
     * 
     * Lấy số lượng chi tiết của một hóa đơn
     * 
     * Response: 200 OK với JSON
     * {
     *   "invoiceId": 100,
     *   "count": 5
     * }
     */
    public Result getInvoiceDetailCount(Http.Request request, Long invoiceId) {
        try {
            int count = invoiceDetailService.getInvoiceDetailCount(invoiceId);
            
            String json = "{\"invoiceId\": " + invoiceId + ", \"count\": " + count + "}";
            return ok(json).withHeader("Content-Type", "application/json");
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * DELETE /api/v1/sales/invoice-details/all/:invoiceId
     * 
     * Xóa tất cả chi tiết của một hóa đơn
     * 
     * Response: 200 OK với JSON
     * {
     *   "invoiceId": 100,
     *   "deletedCount": 5
     * }
     */
    public Result deleteAllInvoiceDetails(Http.Request request, Long invoiceId) {
        try {
            int deletedCount = invoiceDetailService.deleteAllInvoiceDetails(invoiceId);
            
            String json = "{\"invoiceId\": " + invoiceId + ", \"deletedCount\": " + deletedCount + "}";
            return ok(json).withHeader("Content-Type", "application/json");
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Convert InvoiceDetail to JSON string
     */
    private String toJson(InvoiceDetail detail) {
        return "{" +
                "\"invoiceDetailId\": " + detail.getInvoiceDetailId() + "," +
                "\"invoiceId\": " + detail.getInvoiceId() + "," +
                "\"drugId\": " + detail.getDrugId() + "," +
                "\"unitId\": " + detail.getUnitId() + "," +
                "\"quantity\": " + detail.getQuantity() + "," +
                "\"quantityBase\": " + detail.getQuantityBase() + "," +
                "\"unitPrice\": " + detail.getUnitPrice() + "," +
                "\"discountAmount\": " + detail.getDiscountAmount() + "," +
                "\"lineTotal\": " + detail.getLineTotal() +
                "}";
    }
    
    /**
     * Convert List<InvoiceDetail> to JSON string
     */
    private String toJsonArray(List<InvoiceDetail> details) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < details.size(); i++) {
            sb.append(toJson(details.get(i)));
            if (i < details.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}

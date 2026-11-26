package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.SalesReturn;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.SalesReturnService;

import javax.inject.Inject;
import java.util.*;

import static play.mvc.Results.*;

/**
 * SalesReturnController - REST API cho quản lý phiếu trả hàng
 * 
 * Base Path: /api/v1/sales/returns
 * 
 * Endpoints:
 * - POST /create - Lập phiếu trả mới
 * - GET /:id - Lấy phiếu trả theo ID
 * - GET /by-code/:code - Lấy phiếu trả theo mã
 * - POST /:id/approve - Duyệt phiếu trả
 * - POST /:id/cancel - Hủy phiếu trả
 * - DELETE /:id - Xóa phiếu trả
 * - GET /by-invoice/:invoiceId - Lấy phiếu trả của hóa đơn
 * - GET /by-user/:userId - Lấy phiếu trả của user
 * - GET /draft/all - Lấy tất cả phiếu trả DRAFT
 * - GET /count/:invoiceId - Lấy số lượng phiếu trả
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class SalesReturnController extends Controller {
    
    private final SalesReturnService salesReturnService;
    
    @Inject
    public SalesReturnController(SalesReturnService salesReturnService) {
        this.salesReturnService = salesReturnService;
    }
    
    /**
     * POST /api/v1/sales/returns/create
     * 
     * Lập phiếu trả mới
     * 
     * Request Body:
     * {
     *   "returnCode": "TH-001",
     *   "invoiceId": 100,
     *   "createdBy": 1,
     *   "returnReasonCode": "DAMAGED",
     *   "note": "Hàng bị hỏng"
     * }
     * 
     * Response: 201 Created
     */
    public Result createSalesReturn(Http.Request request) {
        try {
            JsonNode json = request.body().asJson();
            
            if (json == null) {
                return badRequest("{\"error\": \"Invalid JSON\"}");
            }
            
            // Parse request
            String returnCode = json.has("returnCode") ? json.get("returnCode").asText() : null;
            Long invoiceId = json.has("invoiceId") ? json.get("invoiceId").asLong() : null;
            Long createdBy = json.has("createdBy") ? json.get("createdBy").asLong() : null;
            String returnReasonCode = json.has("returnReasonCode") ? json.get("returnReasonCode").asText() : null;
            String note = json.has("note") ? json.get("note").asText() : null;
            
            // Create SalesReturn
            SalesReturn salesReturn = new SalesReturn();
            salesReturn.setReturnCode(returnCode);
            salesReturn.setInvoiceId(invoiceId);
            salesReturn.setCreatedBy(createdBy);
            salesReturn.setReturnReasonCode(returnReasonCode);
            salesReturn.setNote(note);
            
            // Create via service
            SalesReturn created = salesReturnService.createSalesReturn(salesReturn);
            
            return created(toJson(created)).withHeader("Content-Type", "application/json");
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/returns/:id
     * 
     * Lấy phiếu trả theo ID
     */
    public Result getSalesReturn(Http.Request request, Long id) {
        try {
            Optional<SalesReturn> salesReturn = salesReturnService.getSalesReturnById(id);
            
            if (!salesReturn.isPresent()) {
                return notFound("{\"error\": \"SalesReturn not found\"}");
            }
            
            return ok(toJson(salesReturn.get())).withHeader("Content-Type", "application/json");
            
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/returns/by-code/:code
     * 
     * Lấy phiếu trả theo mã phiếu
     */
    public Result getSalesReturnByCode(Http.Request request, String code) {
        try {
            Optional<SalesReturn> salesReturn = salesReturnService.getSalesReturnByCode(code);
            
            if (!salesReturn.isPresent()) {
                return notFound("{\"error\": \"SalesReturn not found with code: " + code + "\"}");
            }
            
            return ok(toJson(salesReturn.get())).withHeader("Content-Type", "application/json");
            
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * POST /api/v1/sales/returns/:id/approve
     * 
     * Duyệt phiếu trả
     */
    public Result approveSalesReturn(Http.Request request, Long id) {
        try {
            SalesReturn approved = salesReturnService.approveSalesReturn(id);
            
            return ok(toJson(approved)).withHeader("Content-Type", "application/json");
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (IllegalStateException e) {
            return status(409, "{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * POST /api/v1/sales/returns/:id/cancel
     * 
     * Hủy phiếu trả
     */
    public Result cancelSalesReturn(Http.Request request, Long id) {
        try {
            JsonNode json = request.body().asJson();
            String cancelReason = (json != null && json.has("cancelReason")) 
                ? json.get("cancelReason").asText() 
                : "Hủy theo yêu cầu";
            
            SalesReturn cancelled = salesReturnService.cancelSalesReturn(id, cancelReason);
            
            return ok(toJson(cancelled)).withHeader("Content-Type", "application/json");
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (IllegalStateException e) {
            return status(409, "{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * DELETE /api/v1/sales/returns/:id
     * 
     * Xóa phiếu trả (chỉ được xóa phiếu DRAFT)
     */
    public Result deleteSalesReturn(Http.Request request, Long id) {
        try {
            boolean deleted = salesReturnService.deleteSalesReturn(id);
            
            if (!deleted) {
                return notFound("{\"error\": \"SalesReturn not found\"}");
            }
            
            return noContent();
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (IllegalStateException e) {
            return status(409, "{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/returns/by-invoice/:invoiceId
     * 
     * Lấy tất cả phiếu trả của một hóa đơn
     */
    public Result getSalesReturnsByInvoiceId(Http.Request request, Long invoiceId) {
        try {
            List<SalesReturn> salesReturns = salesReturnService.getSalesReturnsByInvoiceId(invoiceId);
            
            return ok(toJsonArray(salesReturns)).withHeader("Content-Type", "application/json");
            
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/returns/by-user/:userId
     * 
     * Lấy tất cả phiếu trả của một user
     */
    public Result getSalesReturnsByUserId(Http.Request request, Long userId) {
        try {
            List<SalesReturn> salesReturns = salesReturnService.getSalesReturnsByUserId(userId);
            
            return ok(toJsonArray(salesReturns)).withHeader("Content-Type", "application/json");
            
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/returns/draft/all
     * 
     * Lấy tất cả phiếu trả có status = DRAFT
     */
    public Result getDraftSalesReturns(Http.Request request) {
        try {
            List<SalesReturn> draftReturns = salesReturnService.getDraftSalesReturns();
            
            return ok(toJsonArray(draftReturns)).withHeader("Content-Type", "application/json");
            
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/returns/count/:invoiceId
     * 
     * Lấy số lượng phiếu trả của một hóa đơn
     */
    public Result getSalesReturnCount(Http.Request request, Long invoiceId) {
        try {
            int count = salesReturnService.getSalesReturnCountByInvoiceId(invoiceId);
            
            String json = "{\"invoiceId\": " + invoiceId + ", \"count\": " + count + "}";
            return ok(json).withHeader("Content-Type", "application/json");
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Convert SalesReturn to JSON string
     */
    private String toJson(SalesReturn salesReturn) {
        return "{" +
                "\"salesReturnId\": " + salesReturn.getSalesReturnId() + "," +
                "\"returnCode\": \"" + salesReturn.getReturnCode() + "\"," +
                "\"invoiceId\": " + salesReturn.getInvoiceId() + "," +
                "\"createdBy\": " + salesReturn.getCreatedBy() + "," +
                "\"returnDate\": \"" + salesReturn.getReturnDate() + "\"," +
                "\"status\": \"" + salesReturn.getStatus() + "\"," +
                "\"returnReasonCode\": \"" + (salesReturn.getReturnReasonCode() != null ? salesReturn.getReturnReasonCode() : "") + "\"," +
                "\"note\": \"" + (salesReturn.getNote() != null ? salesReturn.getNote() : "") + "\"" +
                "}";
    }
    
    /**
     * Convert List<SalesReturn> to JSON string
     */
    private String toJsonArray(List<SalesReturn> salesReturns) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < salesReturns.size(); i++) {
            sb.append(toJson(salesReturns.get(i)));
            if (i < salesReturns.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}

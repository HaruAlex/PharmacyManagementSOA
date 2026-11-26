package controllers;

import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import play.libs.Json;

import models.Shift;
import models.Shift.ShiftStatus;
import services.ShiftService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/**
 * SalesShiftController - REST API cho Shift (Ca làm việc)
 * 
 * Spec: UC-BH-08 "Mở ca, chốt ca"
 * - FR-BH-12: Mở ca, chốt ca: tổng hợp tiền theo ca, đối chiếu tiền mặt thực tế, ghi chênh lệch
 * 
 * API Endpoints (theo spec 2.6.5 Sales Service):
 * - POST /sales/shifts/open {userId, branchId, openingCash}
 * - GET /sales/shifts/current (ca đang mở của user - header X-User-Id, X-Branch-Id)
 * - POST /sales/shifts/{id}/close {actualClosingCash}
 * - GET /sales/shifts/{id} (chi tiết shift)
 * - GET /sales/shifts/{id}/cash-difference (tính chênh lệch)
 * 
 * HTTP Status Codes:
 * - 200: OK
 * - 201: Created
 * - 204: No Content
 * - 400: Bad Request
 * - 404: Not Found
 * - 409: Conflict (user đã có ca mở)
 * - 422: Unprocessable Entity (business logic error)
 * - 500: Internal Server Error
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class SalesShiftController extends Controller {
    
    private final ShiftService shiftService;
    
    @Inject
    public SalesShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }
    
    /**
     * Mở ca mới
     * POST /sales/shifts/open
     * Request: {userId: 1, branchId: 2, openingCash: 500000}
     * Response: 201 {shiftId, userId, branchId, openingCash, openedAt, status}
     */
    public Result openShift(Http.Request request) {
        try {
            JsonNode body = request.body().asJson();
            
            if (body == null) {
                return badRequest(errorJson("INVALID_REQUEST", "Request body không được null"));
            }
            
            Long userId = body.has("userId") ? body.get("userId").asLong() : null;
            Long branchId = body.has("branchId") ? body.get("branchId").asLong() : null;
            BigDecimal openingCash = body.has("openingCash") ? 
                new BigDecimal(body.get("openingCash").asText()) : null;
            
            if (userId == null || branchId == null || openingCash == null) {
                return badRequest(errorJson("MISSING_FIELD", 
                    "Missing userId, branchId, hoặc openingCash"));
            }
            
            // Mở ca
            Shift shift = shiftService.openShift(userId, branchId, openingCash);
            
            // Trả response
            ObjectNode response = buildShiftJson(shift);
            
            return created(response);
            
        } catch (IllegalArgumentException e) {
            return badRequest(errorJson("INVALID_INPUT", e.getMessage()));
        } catch (IllegalStateException e) {
            return status(409, errorJson("SHIFT_CONFLICT", e.getMessage()));
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", "Lỗi mở ca: " + e.getMessage()));
        }
    }
    
    /**
     * Chốt ca
     * POST /sales/shifts/{id}/close
     * Request: {actualClosingCash: 520000}
     * Response: 200 {shiftId, userId, branchId, openingCash, actualClosingCash, 
     *                 cashDifference, openedAt, closedAt, status}
     */
    public Result closeShift(Http.Request request, Long shiftId) {
        try {
            JsonNode body = request.body().asJson();
            
            if (body == null) {
                return badRequest(errorJson("INVALID_REQUEST", "Request body không được null"));
            }
            
            BigDecimal actualClosingCash = body.has("actualClosingCash") ? 
                new BigDecimal(body.get("actualClosingCash").asText()) : null;
            
            if (actualClosingCash == null) {
                return badRequest(errorJson("MISSING_FIELD", "Missing actualClosingCash"));
            }
            
            // Chốt ca
            Shift shift = shiftService.closeShift(shiftId, actualClosingCash);
            
            // Tính chênh lệch
            BigDecimal cashDifference = shift.calculateCashDifference();
            
            // Trả response
            ObjectNode response = buildShiftJsonWithDifference(shift, cashDifference);
            
            return ok(response);
            
        } catch (IllegalArgumentException e) {
            return badRequest(errorJson("INVALID_INPUT", e.getMessage()));
        } catch (IllegalStateException e) {
            return status(422, errorJson("INVALID_STATE", e.getMessage()));
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", "Lỗi chốt ca: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy ca đang mở của user (GET /sales/shifts/current)
     * Header: X-User-Id, X-Branch-Id (từ Gateway hoặc client)
     * Response: 200 {shiftId, ...} hoặc 204 nếu không có ca mở
     */
    public Result getCurrentShift(Http.Request request) {
        try {
            String userIdStr = request.header("X-User-Id").orElse(null);
            String branchIdStr = request.header("X-Branch-Id").orElse(null);
            
            if (userIdStr == null || branchIdStr == null) {
                return badRequest(errorJson("MISSING_HEADER", 
                    "Missing X-User-Id hoặc X-Branch-Id header"));
            }
            
            Long userId = Long.parseLong(userIdStr);
            Long branchId = Long.parseLong(branchIdStr);
            
            Optional<Shift> shiftOpt = shiftService.getCurrentShift(userId, branchId);
            
            if (!shiftOpt.isPresent()) {
                return noContent(); // 204 No Content - không có ca mở
            }
            
            Shift shift = shiftOpt.get();
            ObjectNode response = buildShiftJson(shift);
            
            return ok(response);
            
        } catch (NumberFormatException e) {
            return badRequest(errorJson("INVALID_HEADER", 
                "X-User-Id hoặc X-Branch-Id không phải số"));
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", 
                "Lỗi lấy ca hiện tại: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy chi tiết shift theo ID
     * GET /sales/shifts/{id}
     * Response: 200 {shiftId, userId, branchId, ...}
     */
    public Result getShift(Http.Request request, Long shiftId) {
        try {
            Optional<Shift> shiftOpt = shiftService.getShiftById(shiftId);
            
            if (!shiftOpt.isPresent()) {
                return notFound(errorJson("NOT_FOUND", 
                    "Shift ID " + shiftId + " không tìm thấy"));
            }
            
            Shift shift = shiftOpt.get();
            ObjectNode response = buildShiftJson(shift);
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", 
                "Lỗi lấy shift: " + e.getMessage()));
        }
    }
    
    /**
     * Tính chênh lệch tiền
     * GET /sales/shifts/{id}/cash-difference
     * Response: 200 {shiftId, openingCash, actualClosingCash, difference}
     */
    public Result getCashDifference(Http.Request request, Long shiftId) {
        try {
            Optional<Shift> shiftOpt = shiftService.getShiftById(shiftId);
            
            if (!shiftOpt.isPresent()) {
                return notFound(errorJson("NOT_FOUND", 
                    "Shift ID " + shiftId + " không tìm thấy"));
            }
            
            Shift shift = shiftOpt.get();
            
            if (shift.getStatus() != ShiftStatus.CLOSED) {
                return status(422, errorJson("NOT_CLOSED", 
                    "Ca chưa được chốt, không thể tính chênh lệch"));
            }
            
            BigDecimal difference = shift.calculateCashDifference();
            
            ObjectNode response = Json.newObject();
            response.put("shiftId", shiftId);
            response.put("openingCash", shift.getOpeningCash().toPlainString());
            response.put("actualClosingCash", shift.getActualClosingCash().toPlainString());
            response.put("difference", difference.toPlainString());
            
            return ok(response);
            
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", 
                "Lỗi tính chênh lệch: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy lịch sử shifts của user
     * GET /sales/shifts/user/{userId}?page=1&size=20
     * Response: 200 {items: [...], page, size, total}
     */
    public Result getUserShiftHistory(Http.Request request, Long userId) {
        try {
            String pageStr = request.queryString().getOrDefault("page", new String[]{"1"})[0];
            String sizeStr = request.queryString().getOrDefault("size", new String[]{"20"})[0];
            
            int page = Integer.parseInt(pageStr);
            int pageSize = Integer.parseInt(sizeStr);
            
            if (page < 1 || pageSize < 1) {
                return badRequest(errorJson("INVALID_PARAM", 
                    "page và size phải >= 1"));
            }
            
            List<Shift> shifts = shiftService.getUserShiftHistory(userId, page, pageSize);
            
            ArrayNode itemsArray = Json.mapper().createArrayNode();
            for (Shift shift : shifts) {
                itemsArray.add(buildShiftJson(shift));
            }
            
            ObjectNode response = Json.newObject();
            response.set("items", itemsArray);
            response.put("page", page);
            response.put("size", pageSize);
            response.put("total", shifts.size());
            
            return ok(response);
            
        } catch (NumberFormatException e) {
            return badRequest(errorJson("INVALID_PARAM", 
                "page hoặc size không phải số"));
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", 
                "Lỗi lấy lịch sử: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy tất cả shifts mở của chi nhánh
     * GET /sales/shifts/branch/{branchId}/open
     * Response: 200 [{shiftId, userId, ...}, ...]
     */
    public Result getOpenShiftsByBranch(Http.Request request, Long branchId) {
        try {
            List<Shift> shifts = shiftService.getOpenShiftsByBranch(branchId);
            
            ArrayNode itemsArray = Json.mapper().createArrayNode();
            for (Shift shift : shifts) {
                itemsArray.add(buildShiftJson(shift));
            }
            
            return ok(itemsArray);
            
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", 
                "Lỗi lấy shifts mở: " + e.getMessage()));
        }
    }
    
    /**
     * Lấy shifts của chi nhánh theo ngày
     * GET /sales/shifts/branch/{branchId}/date?date=2026-11-26
     * Response: 200 [{shiftId, userId, ...}, ...]
     */
    public Result getShiftsByBranchAndDate(Http.Request request, Long branchId) {
        try {
            String dateStr = request.queryString().getOrDefault("date", new String[]{null})[0];
            
            if (dateStr == null) {
                return badRequest(errorJson("MISSING_PARAM", 
                    "Missing date parameter (YYYY-MM-DD)"));
            }
            
            LocalDate date = LocalDate.parse(dateStr);
            List<Shift> shifts = shiftService.getShiftsByBranchAndDate(branchId, date);
            
            ArrayNode itemsArray = Json.mapper().createArrayNode();
            for (Shift shift : shifts) {
                itemsArray.add(buildShiftJson(shift));
            }
            
            return ok(itemsArray);
            
        } catch (IllegalArgumentException e) {
            return badRequest(errorJson("INVALID_DATE", 
                "Date format không hợp lệ (YYYY-MM-DD)"));
        } catch (Exception e) {
            return internalServerError(errorJson("ERROR", 
                "Lỗi lấy shifts theo ngày: " + e.getMessage()));
        }
    }
    
    // ===== HELPER METHODS =====
    
    /**
     * Build JSON object từ Shift
     */
    private ObjectNode buildShiftJson(Shift shift) {
        ObjectNode json = Json.newObject();
        json.put("shiftId", shift.getShiftId());
        json.put("userId", shift.getUserId());
        json.put("branchId", shift.getBranchId());
        json.put("openingCash", shift.getOpeningCash().toPlainString());
        if (shift.getActualClosingCash() != null) {
            json.put("actualClosingCash", shift.getActualClosingCash().toPlainString());
        }
        json.put("openedAt", shift.getOpenedAt().toString());
        if (shift.getClosedAt() != null) {
            json.put("closedAt", shift.getClosedAt().toString());
        }
        json.put("status", shift.getStatus().name());
        
        return json;
    }
    
    /**
     * Build JSON object từ Shift + cash difference
     */
    private ObjectNode buildShiftJsonWithDifference(Shift shift, BigDecimal difference) {
        ObjectNode json = buildShiftJson(shift);
        if (difference != null) {
            json.put("cashDifference", difference.toPlainString());
        }
        return json;
    }
    
    /**
     * Build error JSON
     */
    private ObjectNode errorJson(String code, String message) {
        ObjectNode json = Json.newObject();
        json.put("code", code);
        json.put("message", message);
        return json;
    }
}

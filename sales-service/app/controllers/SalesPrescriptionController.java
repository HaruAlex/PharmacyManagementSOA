package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.Prescription;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.PrescriptionService;
import clients.InventoryClient;

import javax.inject.Inject;
import java.time.LocalDate;
import java.util.*;

import static play.mvc.Results.*;

/**
 * SalesPrescriptionController - REST API cho quản lý đơn thuốc
 * 
 * Base Path: /api/v1/sales/prescriptions
 * 
 * Features:
 * - Lập đơn thuốc
 * - Tìm kiếm đơn thuốc
 * - Tích hợp Inventory API - trừ tồn theo FEFO
 * 
 * @author Nhóm 4 - Đại học Quy Nhơn
 */
public class SalesPrescriptionController extends Controller {
    
    private final PrescriptionService prescriptionService;
    private final InventoryClient inventoryClient;
    
    @Inject
    public SalesPrescriptionController(PrescriptionService prescriptionService, InventoryClient inventoryClient) {
        this.prescriptionService = prescriptionService;
        this.inventoryClient = inventoryClient;
    }
    
    /**
     * POST /api/v1/sales/prescriptions/create
     * 
     * Lập đơn thuốc mới
     * 
     * Request Body:
     * {
     *   "prescriptionCode": "ĐT-001",
     *   "customerId": 100,
     *   "prescriberName": "Dr. Trần",
     *   "facilityName": "Bệnh viện A",
     *   "prescriptionDate": "2026-11-26",
     *   "notes": "Đơn thuốc cảm cúm",
     *   "createdBy": 1
     * }
     */
    public Result createPrescription(Http.Request request) {
        try {
            JsonNode json = request.body().asJson();
            
            if (json == null) {
                return badRequest("{\"error\": \"Invalid JSON\"}");
            }
            
            // Parse request
            String prescriptionCode = json.has("prescriptionCode") ? json.get("prescriptionCode").asText() : null;
            Long customerId = json.has("customerId") ? json.get("customerId").asLong() : null;
            String prescriberName = json.has("prescriberName") ? json.get("prescriberName").asText() : null;
            String facilityName = json.has("facilityName") ? json.get("facilityName").asText() : null;
            String prescriptionDateStr = json.has("prescriptionDate") ? json.get("prescriptionDate").asText() : null;
            String notes = json.has("notes") ? json.get("notes").asText() : null;
            Long createdBy = json.has("createdBy") ? json.get("createdBy").asLong() : null;
            
            // Create Prescription
            Prescription prescription = new Prescription();
            prescription.setPrescriptionCode(prescriptionCode);
            if (customerId != null && customerId > 0) {
                prescription.setCustomerId(customerId);
            }
            prescription.setPrescriberName(prescriberName);
            prescription.setFacilityName(facilityName);
            if (prescriptionDateStr != null) {
                prescription.setPrescriptionDate(LocalDate.parse(prescriptionDateStr));
            }
            prescription.setNotes(notes);
            prescription.setCreatedBy(createdBy);
            
            // Create via service
            Prescription created = prescriptionService.createPrescription(prescription);
            
            return created(toJson(created)).withHeader("Content-Type", "application/json");
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/prescriptions/:id
     * 
     * Lấy đơn thuốc theo ID
     */
    public Result getPrescription(Http.Request request, Long id) {
        try {
            Optional<Prescription> prescription = prescriptionService.getPrescriptionById(id);
            
            if (!prescription.isPresent()) {
                return notFound("{\"error\": \"Prescription not found\"}");
            }
            
            return ok(toJson(prescription.get())).withHeader("Content-Type", "application/json");
            
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/prescriptions/by-code/:code
     * 
     * Lấy đơn thuốc theo mã
     */
    public Result getPrescriptionByCode(Http.Request request, String code) {
        try {
            Optional<Prescription> prescription = prescriptionService.getPrescriptionByCode(code);
            
            if (!prescription.isPresent()) {
                return notFound("{\"error\": \"Prescription not found\"}");
            }
            
            return ok(toJson(prescription.get())).withHeader("Content-Type", "application/json");
            
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/prescriptions/by-customer/:customerId
     * 
     * Lấy tất cả đơn thuốc của một khách hàng
     */
    public Result getPrescriptionsByCustomerId(Http.Request request, Long customerId) {
        try {
            List<Prescription> prescriptions = prescriptionService.getPrescriptionsByCustomerId(customerId);
            
            return ok(toJsonArray(prescriptions)).withHeader("Content-Type", "application/json");
            
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/prescriptions/by-date/:date
     * 
     * Lấy đơn thuốc theo ngày
     */
    public Result getPrescriptionsByDate(Http.Request request, String date) {
        try {
            LocalDate prescriptionDate = LocalDate.parse(date);
            List<Prescription> prescriptions = prescriptionService.getPrescriptionsByDate(prescriptionDate);
            
            return ok(toJsonArray(prescriptions)).withHeader("Content-Type", "application/json");
            
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * DELETE /api/v1/sales/prescriptions/:id
     * 
     * Xóa đơn thuốc
     */
    public Result deletePrescription(Http.Request request, Long id) {
        try {
            boolean deleted = prescriptionService.deletePrescription(id);
            
            if (!deleted) {
                return notFound("{\"error\": \"Prescription not found\"}");
            }
            
            return noContent();
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * GET /api/v1/sales/prescriptions/count/:customerId
     * 
     * Lấy số lượng đơn thuốc của khách hàng
     */
    public Result getPrescriptionCount(Http.Request request, Long customerId) {
        try {
            int count = prescriptionService.getPrescriptionCountByCustomerId(customerId);
            
            String json = "{\"customerId\": " + customerId + ", \"count\": " + count + "}";
            return ok(json).withHeader("Content-Type", "application/json");
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    /**
     * POST /api/v1/sales/prescriptions/:prescriptionId/deduct-stock
     * 
     * Trừ tồn kho theo FEFO (First Expired First Out)
     * Tích hợp với Inventory API
     */
    public Result deductStockByFefo(Http.Request request, Long prescriptionId) {
        try {
            JsonNode json = request.body().asJson();
            
            if (json == null) {
                return badRequest("{\"error\": \"Invalid JSON\"}");
            }
            
            Long drugId = json.has("drugId") ? json.get("drugId").asLong() : null;
            java.math.BigDecimal quantity = json.has("quantity") 
                ? java.math.BigDecimal.valueOf(json.get("quantity").asDouble()) 
                : null;
            Long branchId = json.has("branchId") ? json.get("branchId").asLong() : null;
            
            if (drugId == null || quantity == null || branchId == null) {
                return badRequest("{\"error\": \"Missing required fields: drugId, quantity, branchId\"}");
            }
            
            // Gọi Inventory API để lấy tồn kho theo FEFO
            String stockResponse = inventoryClient.getStockByLotFefo(drugId, quantity, branchId);
            
            if (stockResponse == null || !stockResponse.contains("\"lots\"")) {
                return badRequest("{\"error\": \"Invalid response from Inventory API\"}");
            }
            
            // Trích xuất lot allocations từ response
            String lotAllocationsJson = stockResponse.substring(
                stockResponse.indexOf("\"lots\"") + 7,
                stockResponse.lastIndexOf("]") + 1
            );
            
            // Trừ tồn kho qua API
            String deductResponse = inventoryClient.deductStock(drugId, branchId, lotAllocationsJson);
            
            if (deductResponse.contains("\"success\": true")) {
                return ok(deductResponse).withHeader("Content-Type", "application/json");
            } else {
                return badRequest("{\"error\": \"Failed to deduct stock\"}");
            }
            
        } catch (IllegalArgumentException e) {
            return badRequest("{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return internalServerError("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Convert Prescription to JSON string
     */
    private String toJson(Prescription prescription) {
        return "{" +
                "\"prescriptionId\": " + prescription.getPrescriptionId() + "," +
                "\"prescriptionCode\": \"" + prescription.getPrescriptionCode() + "\"," +
                "\"customerId\": " + (prescription.getCustomerId() != null ? prescription.getCustomerId() : "null") + "," +
                "\"prescriberName\": \"" + prescription.getPrescriberName() + "\"," +
                "\"facilityName\": \"" + (prescription.getFacilityName() != null ? prescription.getFacilityName() : "") + "\"," +
                "\"prescriptionDate\": \"" + prescription.getPrescriptionDate() + "\"," +
                "\"notes\": \"" + (prescription.getNotes() != null ? prescription.getNotes() : "") + "\"," +
                "\"createdBy\": " + prescription.getCreatedBy() + "," +
                "\"createdAt\": \"" + prescription.getCreatedAt() + "\"" +
                "}";
    }
    
    /**
     * Convert List<Prescription> to JSON string
     */
    private String toJsonArray(List<Prescription> prescriptions) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < prescriptions.size(); i++) {
            sb.append(toJson(prescriptions.get(i)));
            if (i < prescriptions.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}

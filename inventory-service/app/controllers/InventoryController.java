package controllers;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.StockLot;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import services.InventoryService;

import javax.inject.Inject;
import java.time.LocalDate;
import java.util.List;

/**
 * Controller cung cấp endpoint REST API tra cứu tồn kho theo lô (Stock By Lot - BE-28):
 * - GET /api/inventory/lots?keyword=...
 * - GET /api/inventory/lots/:medicineId
 */
public class InventoryController extends Controller {

    private final InventoryService inventoryService;

    @Inject
    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * GET /api/inventory/lots
     * Trả về danh sách các lô thuốc kèm tổng số lượng tồn cộng dồn.
     */
    public Result getStockByLots(String keyword) {
        try {
            List<StockLot> lots = inventoryService.getStockByLots(keyword);
            int totalQuantity = inventoryService.calculateTotalQuantity(lots);

            ArrayNode lotsArray = Json.newArray();
            for (StockLot lot : lots) {
                lotsArray.add(toLotJson(lot));
            }

            ObjectNode response = Json.newObject();
            response.put("status", "success");
            response.put("lotCount", lots.size());
            response.put("totalQuantity", totalQuantity);
            response.set("lots", lotsArray);

            return ok(response);
        } catch (Exception e) {
            ObjectNode error = Json.newObject();
            error.put("status", "error");
            error.put("message", "Failed to fetch stock by lot");
            return internalServerError(error);
        }
    }

    /**
     * GET /api/inventory/lots/:medicineId
     * Trả về danh sách các lô tồn kho thuộc một loại thuốc.
     */
    public Result getStockByMedicineId(String medicineId) {
        if (medicineId == null || medicineId.trim().isEmpty()) {
            ObjectNode badReq = Json.newObject();
            badReq.put("status", "error");
            badReq.put("message", "Medicine ID is required");
            return badRequest(badReq);
        }

        try {
            List<StockLot> lots = inventoryService.getStockByMedicineId(medicineId);
            if (lots.isEmpty()) {
                ObjectNode notFound = Json.newObject();
                notFound.put("status", "error");
                notFound.put("message", "No stock lots found for medicine ID");
                return notFound(notFound);
            }

            ArrayNode lotsArray = Json.newArray();
            for (StockLot lot : lots) {
                lotsArray.add(toLotJson(lot));
            }

            ObjectNode dataNode = Json.newObject();
            dataNode.put("medicineId", lots.get(0).getMedicineId());
            dataNode.put("medicineName", lots.get(0).getMedicineName());
            dataNode.put("unit", lots.get(0).getUnit());
            dataNode.put("totalQuantity", inventoryService.calculateTotalQuantity(lots));
            dataNode.put("lotCount", lots.size());
            dataNode.set("lots", lotsArray);

            ObjectNode response = Json.newObject();
            response.put("status", "success");
            response.set("data", dataNode);

            return ok(response);
        } catch (Exception e) {
            ObjectNode error = Json.newObject();
            error.put("status", "error");
            error.put("message", "Failed to fetch medicine stock lots");
            return internalServerError(error);
        }
    }

    /**
     * Chuyển đổi đối tượng {@link StockLot} sang JSON trả về cho client.
     */
    private ObjectNode toLotJson(StockLot lot) {
        LocalDate today = LocalDate.now();
        ObjectNode node = Json.newObject();
        node.put("lotNumber", lot.getLotNumber());
        node.put("medicineId", lot.getMedicineId());
        node.put("medicineName", lot.getMedicineName());
        node.put("unit", lot.getUnit());
        node.put("quantity", lot.getQuantity());
        node.put("minStockThreshold", lot.getMinStockThreshold());
        node.put("lowStock", lot.isLowStock());
        node.put("expiryDate", lot.getExpiryDate() != null ? lot.getExpiryDate().toString() : null);
        node.put("expired", lot.isExpired(today));
        node.put("updatedAt", lot.getUpdatedAt() != null ? lot.getUpdatedAt().toString() : null);
        return node;
    }
}
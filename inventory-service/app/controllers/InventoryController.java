package controllers;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.MedicineStock;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.InventoryService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * Controller cung cấp REST API xem tồn kho cho BE-28.
 * Tiếp nhận HTTP request, gọi {@link InventoryService} và định dạng JSON phản hồi.
 */
@Singleton
public class InventoryController extends Controller {

    private final InventoryService inventoryService;

    /**
     * Tiêm {@link InventoryService} vào Controller.
     *
     * @param inventoryService tầng xử lý nghiệp vụ tồn kho
     */
    @Inject
    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Xử lý {@code GET /api/inventory?keyword=...}.
     * Trả về danh sách tồn kho cùng tổng số bản ghi.
     *
     * @param request HTTP request chứa query param tùy chọn {@code keyword}
     * @return HTTP 200 kèm JSON danh sách tồn kho hoặc HTTP 500 nếu có lỗi hệ thống
     */
    public Result getStocks(Http.Request request) {
        String keyword = request.queryString("keyword").orElse("");
        try {
            List<MedicineStock> stocks = inventoryService.getStocks(keyword);

            // Chuyển danh sách model sang mảng JSON trả về cho client
            ArrayNode items = Json.newArray();
            for (MedicineStock stock : stocks) {
                items.add(toJson(stock));
            }

            ObjectNode response = Json.newObject();
            response.put("status", "success");
            response.put("count", stocks.size());
            response.set("data", items);
            return ok(response);
        } catch (Exception e) {
            ObjectNode error = Json.newObject();
            error.put("status", "error");
            error.put("message", "Failed to fetch inventory stocks");
            return internalServerError(error);
        }
    }

    /**
     * Xử lý {@code GET /api/inventory/:medicineId}.
     * Trả về thông tin tồn kho chi tiết của một loại thuốc theo mã.
     *
     * @param medicineId mã thuốc truyền trên đường dẫn URL
     * @return HTTP 200 nếu tìm thấy, 400 nếu ID rỗng, 404 nếu không tồn tại, 500 nếu lỗi hệ thống
     */
    public Result getStockByMedicineId(String medicineId) {
        try {
            Optional<MedicineStock> stockOpt = inventoryService.getStockByMedicineId(medicineId);
            if (stockOpt.isEmpty()) {
                ObjectNode notFound = Json.newObject();
                notFound.put("status", "error");
                notFound.put("message", "Medicine stock not found");
                return notFound(notFound);
            }

            ObjectNode response = Json.newObject();
            response.put("status", "success");
            response.set("data", toJson(stockOpt.get()));
            return ok(response);
        } catch (IllegalArgumentException e) {
            ObjectNode badRequest = Json.newObject();
            badRequest.put("status", "error");
            badRequest.put("message", e.getMessage());
            return badRequest(badRequest);
        } catch (Exception e) {
            ObjectNode error = Json.newObject();
            error.put("status", "error");
            error.put("message", "Failed to fetch medicine stock");
            return internalServerError(error);
        }
    }

    /**
     * Quy đổi {@link MedicineStock} sang cấu trúc JSON thống nhất cho API.
     *
     * @param stock bản ghi tồn kho cần chuyển đổi
     * @return {@link ObjectNode} chứa các trường dữ liệu tồn kho
     */
    private ObjectNode toJson(MedicineStock stock) {
        ObjectNode node = Json.newObject();
        node.put("medicineId", stock.getMedicineId());
        node.put("medicineName", stock.getMedicineName());
        node.put("unit", stock.getUnit());
        node.put("totalQuantity", stock.getTotalQuantity());
        node.put("minStockThreshold", stock.getMinStockThreshold());
        node.put("lowStock", stock.isLowStock());
        node.put("updatedAt", stock.getUpdatedAt() != null ? stock.getUpdatedAt().toString() : null);
        return node;
    }
}
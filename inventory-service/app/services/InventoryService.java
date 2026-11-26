package services;

import models.StockLot;
import repositories.InventoryRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Tầng nghiệp vụ xử lý tra cứu tồn kho theo từng lô thuốc (Stock By Lot - BE-28).
 */
@Singleton
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Inject
    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Lấy danh sách các lô tồn kho theo từ khóa tìm kiếm tùy chọn.
     */
    public List<StockLot> getStockByLots(String keyword) {
        return inventoryRepository.findAllLots(keyword);
    }

    /**
     * Lấy danh sách các lô tồn kho theo mã thuốc.
     */
    public List<StockLot> getStockByMedicineId(String medicineId) {
        return inventoryRepository.findLotsByMedicineId(medicineId);
    }

    /**
     * Tính tổng số lượng tồn kho cộng dồn từ danh sách lô.
     */
    public int calculateTotalQuantity(List<StockLot> lots) {
        return lots.stream()
                .mapToInt(StockLot::getQuantity)
                .sum();
    }
}
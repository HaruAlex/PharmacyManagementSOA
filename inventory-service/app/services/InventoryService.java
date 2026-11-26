package services;

import models.MedicineStock;
import repositories.InventoryRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Service xử lý nghiệp vụ tra cứu tồn kho cho BE-28.
 * Đứng giữa Controller và Repository để kiểm tra dữ liệu đầu vào và điều phối truy vấn.
 */
@Singleton
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    /**
     * Tiêm {@link InventoryRepository} vào Service.
     *
     * @param inventoryRepository tầng truy cập dữ liệu tồn kho
     */
    @Inject
    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Lấy danh sách tồn kho theo từ khóa lọc (nếu có).
     *
     * @param keyword từ khóa tìm kiếm theo mã hoặc tên thuốc
     * @return danh sách bản ghi tồn kho
     * @throws SQLException nếu tầng Repository gặp lỗi cơ sở dữ liệu
     */
    public List<MedicineStock> getStocks(String keyword) throws SQLException {
        return inventoryRepository.findAll(keyword);
    }

    /**
     * Lấy thông tin tồn kho chi tiết theo mã thuốc sau khi kiểm tra tính hợp lệ của ID.
     *
     * @param medicineId mã thuốc cần tra cứu
     * @return {@link Optional} chứa tồn kho của thuốc nếu có
     * @throws IllegalArgumentException nếu {@code medicineId} null hoặc chỉ chứa khoảng trắng
     * @throws SQLException             nếu tầng Repository gặp lỗi cơ sở dữ liệu
     */
    public Optional<MedicineStock> getStockByMedicineId(String medicineId) throws SQLException {
        // Chặn mã thuốc rỗng ngay ở tầng Service để Controller trả về 400 Bad Request
        if (medicineId == null || medicineId.trim().isEmpty()) {
            throw new IllegalArgumentException("medicineId must not be blank");
        }
        return inventoryRepository.findByMedicineId(medicineId.trim());
    }
}
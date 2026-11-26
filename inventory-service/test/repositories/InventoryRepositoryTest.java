package repositories;

import models.MedicineStock;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit test cho {@link InventoryRepository} (BE-28) với bộ dữ liệu mẫu hiện tại.
 */
public class InventoryRepositoryTest {

    // Repository được khởi tạo mới trước mỗi test case
    private InventoryRepository repository;

    @Before
    public void setUp() {
        repository = new InventoryRepository();
    }

    /**
     * Kiểm tra lấy toàn bộ danh sách mẫu khi không truyền keyword:
     * trả đủ 4 thuốc và sắp xếp tăng dần theo tên thuốc.
     */
    @Test
    public void findAll_withoutKeyword_shouldReturnAllSampleStocksSortedByName() {
        List<MedicineStock> stocks = repository.findAll(null);

        assertEquals(4L, stocks.size());
        assertEquals("Amoxicillin 500mg", stocks.get(0).getMedicineName());
        assertEquals("Omeprazole 20mg", stocks.get(1).getMedicineName());
        assertEquals("Paracetamol 500mg", stocks.get(2).getMedicineName());
        assertEquals("Vitamin C 1000mg", stocks.get(3).getMedicineName());
    }

    /**
     * Kiểm tra lọc theo keyword (không phân biệt hoa/thường) theo cả tên thuốc và mã thuốc.
     */
    @Test
    public void findAll_withKeyword_shouldFilterByNameOrIdCaseInsensitive() {
        List<MedicineStock> byName = repository.findAll("paracetamol");
        assertEquals(1L, byName.size());
        assertEquals("MED-01", byName.get(0).getMedicineId());

        List<MedicineStock> byId = repository.findAll("med-03");
        assertEquals(1L, byId.size());
        assertEquals("Vitamin C 1000mg", byId.get(0).getMedicineName());
    }

    /**
     * Kiểm tra tìm theo medicineId khi tồn tại và xác minh cờ cảnh báo sắp hết hàng (lowStock).
     */
    @Test
    public void findByMedicineId_whenExists_shouldReturnStockWithLowStockFlag() {
        // MED-01 có totalQuantity = 15 <= minStockThreshold = 20 -> lowStock = true
        Optional<MedicineStock> lowStockItem = repository.findByMedicineId("MED-01");
        assertTrue(lowStockItem.isPresent());
        assertEquals(15L, lowStockItem.get().getTotalQuantity());
        assertEquals(20L, lowStockItem.get().getMinStockThreshold());
        assertTrue(lowStockItem.get().isLowStock());

        // MED-02 có tồn kho cao hơn ngưỡng tối thiểu -> lowStock = false
        Optional<MedicineStock> normalStockItem = repository.findByMedicineId("med-02");
        assertTrue(normalStockItem.isPresent());
        assertFalse(normalStockItem.get().isLowStock());
    }

    /**
     * Kiểm tra tìm theo medicineId khi không có bản ghi khớp -> trả về Optional.empty().
     */
    @Test
    public void findByMedicineId_whenNotFound_shouldReturnEmpty() {
        Optional<MedicineStock> result = repository.findByMedicineId("NOT-EXIST");
        assertFalse(result.isPresent());
    }
}
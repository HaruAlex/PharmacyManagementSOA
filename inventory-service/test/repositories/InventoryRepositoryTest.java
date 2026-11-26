package repositories;

import models.StockLot;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit test cho {@link InventoryRepository} theo mô hình Stock By Lot (BE-28).
 */
public class InventoryRepositoryTest {

    // Repository được làm mới trước mỗi test case
    private InventoryRepository repository;

    @Before
    public void setUp() {
        repository = new InventoryRepository();
    }

    /**
     * Kiểm tra lấy toàn bộ các lô mẫu khi không truyền keyword:
     * trả đủ 5 lô và lô hết hạn sớm nhất đứng đầu (2026-10-01).
     */
    @Test
    public void findAllLots_withoutKeyword_shouldReturnAllSampleLotsSortedByExpiry() {
        List<StockLot> lots = repository.findAllLots(null);

        assertEquals(5L, lots.size());
        assertEquals("LOT-VITC-01", lots.get(0).getLotNumber());
        assertTrue(lots.get(0).isExpired(LocalDate.of(2026, 11, 26)));
    }

    /**
     * Kiểm tra lọc lô theo từ khóa: khớp theo mã lô, mã thuốc hoặc tên thuốc (không phân biệt hoa/thường).
     */
    @Test
    public void findAllLots_withKeyword_shouldFilterByLotNumberOrMedicine() {
        // Tìm theo mã lô
        List<StockLot> byLot = repository.findAllLots("lot-para-02");
        assertEquals(1L, byLot.size());
        assertEquals(45L, byLot.get(0).getQuantity());
        assertFalse(byLot.get(0).isLowStock());

        // Tìm theo tên thuốc có 2 lô (LOT-PARA-01 và LOT-PARA-02)
        List<StockLot> byMedicineName = repository.findAllLots("paracetamol");
        assertEquals(2L, byMedicineName.size());
    }

    /**
     * Kiểm tra lấy tất cả các lô thuộc một medicineId và kiểm tra trạng thái lowStock của từng lô.
     */
    @Test
    public void findLotsByMedicineId_whenExists_shouldReturnLotsOfMedicine() {
        List<StockLot> med01Lots = repository.findLotsByMedicineId("med-01");

        assertEquals(2L, med01Lots.size());
        // Lô đầu tiên hạn 2027-06-30 có quantity = 15 <= threshold = 20 -> lowStock = true
        assertEquals("LOT-PARA-01", med01Lots.get(0).getLotNumber());
        assertTrue(med01Lots.get(0).isLowStock());
        // Lô thứ hai hạn 2027-12-15 có quantity = 45 > threshold = 20 -> lowStock = false
        assertEquals("LOT-PARA-02", med01Lots.get(1).getLotNumber());
        assertFalse(med01Lots.get(1).isLowStock());
    }

    /**
     * Kiểm tra khi truyền medicineId không tồn tại -> trả về danh sách rỗng.
     */
    @Test
    public void findLotsByMedicineId_whenNotFound_shouldReturnEmptyList() {
        List<StockLot> result = repository.findLotsByMedicineId("UNKNOWN-ID");
        assertTrue(result.isEmpty());
    }
}
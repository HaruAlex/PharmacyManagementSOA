package services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import models.DrugLotDto;
import models.PagedResponse;
import models.StockLot;
import org.junit.Before;
import org.junit.Test;
import repositories.InventoryRepository;

/**
 * Bộ unit test kiểm thử tầng nghiệp vụ DrugLotService cho tính năng BE-29 (Quản lý lô thuốc):
 * - Phân loại hạn sử dụng (HET_HAN, SAP_HET_HAN, CON_HAN)
 * - Ánh xạ thực thể StockLot sang DrugLotDto
 * - Lọc theo thuốc (drugId), trạng thái hạn dùng (status), sắp xếp và phân trang
 * - Truy vấn chi tiết lô thuốc theo lotId và chi nhánh
 */
public class DrugLotServiceTest {

    // Đối tượng service cần kiểm thử.
    private DrugLotService drugLotService;

    /**
     * Khởi tạo lại service với repository mẫu trước mỗi test case để đảm bảo tính độc lập.
     */
    @Before
    public void setUp() {
        this.drugLotService = new DrugLotService(new InventoryRepository());
    }

    /**
     * Kiểm tra ngày hết hạn trước ngày mốc tham chiếu phải trả về trạng thái HET_HAN.
     */
    @Test
    public void resolveExpiryStatus_shouldReturnHetHanWhenBeforeReferenceDate() {
        LocalDate refDate = LocalDate.of(2026, 11, 26);
        LocalDate expiredDate = LocalDate.of(2026, 10, 1);

        String status = drugLotService.resolveExpiryStatus(expiredDate, refDate);
        assertEquals("HET_HAN", status);
    }

    /**
     * Kiểm tra ngày hết hạn nằm trong khoảng 0..30 ngày kể từ ngày mốc (bao gồm cả ngày mốc
     * và biên 30 ngày) phải trả về trạng thái SAP_HET_HAN.
     */
    @Test
    public void resolveExpiryStatus_shouldReturnSapHetHanWithin30Days() {
        LocalDate refDate = LocalDate.of(2026, 11, 26);

        // Đúng mốc 30 ngày tiếp theo -> vẫn tính là sắp hết hạn.
        LocalDate boundaryDate = refDate.plusDays(30);
        assertEquals("SAP_HET_HAN", drugLotService.resolveExpiryStatus(boundaryDate, refDate));

        // Hết hạn đúng vào ngày tham chiếu (còn 0 ngày) -> vẫn tính là sắp hết hạn.
        assertEquals("SAP_HET_HAN", drugLotService.resolveExpiryStatus(refDate, refDate));
    }

    /**
     * Kiểm tra ngày hết hạn sau hơn 30 ngày hoặc bị null phải trả về CON_HAN để an toàn dữ liệu.
     */
    @Test
    public void resolveExpiryStatus_shouldReturnConHanWhenMoreThan30DaysOrNull() {
        LocalDate refDate = LocalDate.of(2026, 11, 26);

        assertEquals("CON_HAN", drugLotService.resolveExpiryStatus(refDate.plusDays(31), refDate));
        assertEquals("CON_HAN", drugLotService.resolveExpiryStatus(null, refDate));
    }

    /**
     * Kiểm tra hàm toDto ánh xạ đúng toàn bộ trường từ StockLot sang DrugLotDto,
     * gán đúng thông tin chi nhánh và tính đúng trạng thái hạn dùng.
     */
    @Test
    public void toDto_shouldMapFieldsFromStockLotCorrectly() {
        StockLot lot =
                new StockLot(
                        "LOT-TEST-01",
                        "MED-01",
                        "Paracetamol 500mg",
                        "Hop",
                        25,
                        10,
                        LocalDate.of(2027, 6, 30),
                        Instant.parse("2026-11-20T08:00:00Z"));

        DrugLotDto dto = drugLotService.toDto(lot, 2L, 10L, LocalDate.of(2026, 11, 26));

        assertEquals(Long.valueOf(10L), dto.getLotId());
        assertEquals("MED-01", dto.getDrugCode());
        assertEquals("Paracetamol 500mg", dto.getDrugName());
        assertEquals("LOT-TEST-01", dto.getLotNumber());
        assertEquals(LocalDate.of(2027, 6, 30), dto.getExpiryDate());
        assertEquals(Long.valueOf(2L), dto.getBranchId());
        assertEquals("Chi nhánh #2", dto.getBranchName());
        assertEquals(Long.valueOf(25L), dto.getQuantityBase());
        assertEquals(BigDecimal.ZERO, dto.getCostBase());
        assertEquals("CON_HAN", dto.getExpiryStatus());
    }

    /**
     * Kiểm tra lọc danh sách theo drugId = 1 (khớp MED-01) và sắp xếp tăng dần theo expiryDate.
     */
    @Test
    public void getLots_shouldFilterByDrugIdAndSortByExpiryDate() {
        PagedResponse response = drugLotService.getLots(1L, 1L, null, 0, 10, "expiryDate");

        assertEquals(0, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(2L, response.getTotal());
        assertEquals(2, response.getItems().size());

        // LOT-PARA-01 hết hạn 2027-06-30 phải đứng trước LOT-PARA-02 hết hạn 2027-12-15.
        DrugLotDto first = (DrugLotDto) response.getItems().get(0);
        DrugLotDto second = (DrugLotDto) response.getItems().get(1);
        assertEquals("LOT-PARA-01", first.getLotNumber());
        assertEquals("LOT-PARA-02", second.getLotNumber());
    }

    /**
     * Kiểm tra lọc danh sách theo trạng thái hạn sử dụng status = HET_HAN.
     */
    @Test
    public void getLots_shouldFilterByExpiryStatus() {
        PagedResponse expiredOnly = drugLotService.getLots(1L, null, "HET_HAN", 0, 10, null);

        assertEquals(1L, expiredOnly.getTotal());
        assertEquals(1, expiredOnly.getItems().size());
        DrugLotDto item = (DrugLotDto) expiredOnly.getItems().get(0);
        assertEquals("LOT-VITC-01", item.getLotNumber());
        assertEquals("HET_HAN", item.getExpiryStatus());
    }

    /**
     * Kiểm tra truyền tham số phân trang không hợp lệ (page < 0 hoặc size <= 0)
     * thì phải ném IllegalArgumentException.
     */
    @Test
    public void getLots_shouldThrowIllegalArgumentExceptionWhenPaginationInvalid() {
        assertThrows(
                IllegalArgumentException.class,
                () -> drugLotService.getLots(1L, null, null, -1, 10, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> drugLotService.getLots(1L, null, null, 0, 0, null));
    }

    /**
     * Kiểm tra hàm paginate cắt đúng trang cần lấy và giữ nguyên tổng số bản ghi (total).
     */
    @Test
    public void paginate_shouldSliceItemsSafely() {
        List<Object> items = List.of("A", "B", "C", "D", "E");

        // Trang 1 với kích thước 2 trên tổng 5 phần tử -> lấy phần tử chỉ số 2 và 3 ("C", "D").
        PagedResponse page1 = drugLotService.paginate(items, 1, 2);

        assertEquals(1, page1.getPage());
        assertEquals(2, page1.getSize());
        assertEquals(5L, page1.getTotal());
        assertEquals(List.of("C", "D"), page1.getItems());
    }

    /**
     * Kiểm tra lấy chi tiết lô thuốc: trả về Optional có dữ liệu khi tồn tại
     * và Optional rỗng khi lotId không tồn tại.
     */
    @Test
    public void getLotDetail_shouldReturnLotWhenFoundAndEmptyWhenMissing() {
        Optional<DrugLotDto> found = drugLotService.getLotDetail(1L, 1L);
        assertTrue(found.isPresent());
        assertEquals(Long.valueOf(1L), found.get().getLotId());
        assertEquals(Long.valueOf(1L), found.get().getBranchId());

        Optional<DrugLotDto> missing = drugLotService.getLotDetail(999L, 1L);
        assertFalse(missing.isPresent());
    }
}
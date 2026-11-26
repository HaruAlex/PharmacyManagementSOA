package services;

import models.PagedResponse;
import models.StockLedger;
import models.StockLedgerDto;
import org.junit.Before;
import org.junit.Test;
import play.libs.concurrent.HttpExecutionContext;
import repositories.StockLedgerRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho StockLedgerService (BE-30 / FR-KH-03).
 * Sử dụng Mockito để giả lập Repository và HttpExecutionContext, chạy nhanh không cần DB thật.
 */
public class StockLedgerServiceTest {

    private StockLedgerRepository mockRepository;
    private HttpExecutionContext mockExecutionContext;
    private StockLedgerService service;

    @Before
    public void setUp() {
        mockRepository = mock(StockLedgerRepository.class);
        mockExecutionContext = mock(HttpExecutionContext.class);

        // Chạy trực tiếp trên luồng test để kiểm thử bất đồng bộ deterministic, không bị race condition
        Executor directExecutor = Runnable::run;
        when(mockExecutionContext.current()).thenReturn(directExecutor);

        service = new StockLedgerService(mockRepository, mockExecutionContext);
    }

    /**
     * Kiểm tra tra cứu thẻ kho thành công và trả về đúng cấu trúc phân trang PagedResponse.
     */
    @Test
    public void getStockLedger_shouldReturnPagedResult_whenParamsValid() throws Exception {
        // Chuẩn bị dữ liệu mẫu cho 1 dòng biến động kho (bán hàng trừ 10 viên, tồn còn 90)
        StockLedgerDto item = new StockLedgerDto();
        item.id = 101L;
        item.branchId = 2;
        item.drugId = 1;
        item.lotId = 5;
        item.lotNumber = "LOT-2026-01";
        item.movementType = "BAN";
        item.quantityChange = -10;
        item.balanceAfter = 90;
        item.unitCost = new BigDecimal("600.00");
        item.referenceType = "INVOICE";
        item.referenceId = 1001L;
        item.referenceCode = "CN02-20261126-0001";
        item.createdBy = 3;
        item.createdAt = LocalDateTime.of(2026, 11, 26, 10, 30, 0);

        PagedResponse expectedResponse = new PagedResponse(List.of(item), 1, 20, 1L);

        when(mockRepository.findLedger(
                eq(2),
                eq(1),
                eq(5),
                eq("BAN"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(1),
                eq(20),
                eq("DESC")
        )).thenReturn(expectedResponse);

        // Thực thi phương thức cần test
        CompletionStage<PagedResponse> stage = service.getStockLedger(
                2, 1, 5, "BAN", "2026-11-01", "2026-11-26", 1, 20, "createdAt,desc"
        );
        PagedResponse actual = stage.toCompletableFuture().get();

        // Kiểm chứng kết quả
        assertNotNull(actual);
        assertEquals(1, actual.getPage());
        assertEquals(20, actual.getSize());
        assertEquals(1L, actual.getTotal());
        assertEquals(1, actual.getItems().size());
        assertSame(item, actual.getItems().get(0));

        verify(mockRepository, times(1)).findLedger(
                eq(2), eq(1), eq(5), eq("BAN"),
                any(LocalDateTime.class), any(LocalDateTime.class),
                eq(1), eq(20), eq("DESC")
        );
    }

    /**
     * Kiểm tra ném IllegalArgumentException khi ngày bắt đầu ('from') lớn hơn ngày kết thúc ('to').
     */
    @Test(expected = IllegalArgumentException.class)
    public void getStockLedger_shouldThrowException_whenFromDateAfterToDate() {
        service.getStockLedger(
                2, null, null, null, "2026-11-30", "2026-11-01", 1, 20, "createdAt,desc"
        );
    }

    /**
     * Kiểm tra ném IllegalArgumentException khi truyền loại biến động kho không nằm trong danh mục cho phép.
     */
    @Test(expected = IllegalArgumentException.class)
    public void getStockLedger_shouldThrowException_whenMovementTypeInvalid() {
        service.getStockLedger(
                2, null, null, "LOAI_KHONG_TON_TAI", null, null, 1, 20, "createdAt,desc"
        );
    }

    /**
     * Kiểm tra ném IllegalArgumentException khi định dạng ngày không đúng chuẩn.
     */
    @Test(expected = IllegalArgumentException.class)
    public void getStockLedger_shouldThrowException_whenDateFormatInvalid() {
        service.getStockLedger(
                2, null, null, null, "26/11/2026", null, 1, 20, "createdAt,desc"
        );
    }

    /**
     * Kiểm tra ghi nhận biến động kho hợp lệ qua recordMovement và trả về ID bản ghi mới.
     */
    @Test
    public void recordMovement_shouldInsertAndReturnId_whenDataValid() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockRepository.insert(eq(mockConn), any(StockLedger.class))).thenReturn(555L);

        Long generatedId = service.recordMovement(
                mockConn,
                2,
                1,
                10,
                "NHAP",
                500,
                500,
                new BigDecimal("600.00"),
                "RECEIPT",
                45L,
                "PN-20261126-001",
                "Nhập lô mới từ nhà cung cấp",
                2
        );

        assertEquals(Long.valueOf(555L), generatedId);
        verify(mockRepository, times(1)).insert(eq(mockConn), any(StockLedger.class));
    }

    /**
     * Kiểm tra không cho phép ghi nhận biến động kho khi quantityChange bằng 0.
     */
    @Test(expected = IllegalArgumentException.class)
    public void recordMovement_shouldThrowException_whenQuantityChangeIsZero() throws Exception {
        Connection mockConn = mock(Connection.class);

        service.recordMovement(
                mockConn,
                2, 1, 10, "DIEU_CHINH",
                0, // Số lượng thay đổi bằng 0 là không hợp lệ
                100,
                null,
                "STOCKTAKE",
                12L,
                "KK-20261126-01",
                "Không có chênh lệch",
                2
        );
    }

    /**
     * Kiểm tra không cho phép tồn kho sau biến động bị âm.
     */
    @Test(expected = IllegalArgumentException.class)
    public void recordMovement_shouldThrowException_whenBalanceAfterNegative() throws Exception {
        Connection mockConn = mock(Connection.class);

        service.recordMovement(
                mockConn,
                2, 1, 10, "BAN",
                -50,
                -10, // Tồn sau biến động âm là vi phạm ràng buộc tồn >= 0
                new BigDecimal("600.00"),
                "INVOICE",
                1002L,
                "CN02-20261126-0002",
                "Bán vượt tồn",
                3
        );
    }
}
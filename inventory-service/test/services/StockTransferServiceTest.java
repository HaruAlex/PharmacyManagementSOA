package services;

import models.PagedResponse;
import models.StockTransferDto;
import models.StockTransferItem;
import org.junit.Before;
import org.junit.Test;
import play.db.Database;
import play.libs.concurrent.HttpExecutionContext;
import repositories.StockTransferRepository;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho StockTransferService (BE-31 / Stock Transfers).
 * Kiểm thử quy tắc chuyển kho, trừ/cộng tồn lô, ghi thẻ kho 2 đầu CHUYEN_XUAT / CHUYEN_NHAN
 * và rollback giao dịch khi xảy ra lỗi.
 */
public class StockTransferServiceTest {

    private StockTransferRepository mockRepo;
    private StockLedgerService mockLedgerService;
    private HttpExecutionContext mockEc;
    private Database mockDb;
    private Connection mockConn;
    private StockTransferService service;

    @Before
    public void setUp() throws Exception {
        mockRepo = mock(StockTransferRepository.class);
        mockLedgerService = mock(StockLedgerService.class);
        mockEc = mock(HttpExecutionContext.class);
        mockDb = mock(Database.class);
        mockConn = mock(Connection.class);

        // Thực thi trực tiếp trên luồng test để kiểm soát bất đồng bộ ổn định
        when(mockEc.current()).thenReturn((Executor) Runnable::run);
        when(mockRepo.getDatabase()).thenReturn(mockDb);
        when(mockDb.getConnection()).thenReturn(mockConn);
        when(mockConn.getAutoCommit()).thenReturn(true);

        service = new StockTransferService(mockRepo, mockLedgerService, mockEc);
    }

    /**
     * Tạo phiếu chuyển kho hợp lệ: lưu phiếu, trừ tồn chi nhánh xuất, cộng tồn chi nhánh nhận,
     * ghi 2 biến động thẻ kho (CHUYEN_XUAT và CHUYEN_NHAN) và commit transaction.
     */
    @Test
    public void createTransfer_shouldTransferStockAndRecordLedger_whenValid() throws Exception {
        StockTransferItem item = new StockTransferItem();
        item.setDrugId(1);
        item.setLotId(10);
        item.setQuantity(25);

        when(mockRepo.insertTransfer(eq(mockConn), any())).thenReturn(88L);
        when(mockRepo.upsertLotStock(eq(mockConn), eq(2), eq(1), eq(10), eq(-25))).thenReturn(75);
        when(mockRepo.upsertLotStock(eq(mockConn), eq(3), eq(1), eq(10), eq(25))).thenReturn(25);

        CompletionStage<StockTransferDto> stage = service.createTransfer(
                2, 3, "Chuyển hàng nhánh 3", 5, List.of(item)
        );
        StockTransferDto result = stage.toCompletableFuture().get();

        // Kiểm tra thông tin phiếu chuyển kho trả về
        assertNotNull(result);
        assertEquals(Long.valueOf(88L), result.id);
        assertEquals(Integer.valueOf(2), result.sourceBranchId);
        assertEquals(Integer.valueOf(3), result.targetBranchId);
        assertEquals("COMPLETED", result.status);
        assertEquals(1, result.items.size());

        // Kiểm tra commit giao dịch và ghi biến động thẻ kho cho cả 2 chi nhánh
        verify(mockConn, times(1)).commit();
        verify(mockLedgerService, times(1)).recordMovement(
                eq(mockConn), eq(2), eq(1), eq(10), eq("CHUYEN_XUAT"),
                eq(-25), eq(75), isNull(), eq("TRANSFER"), eq(88L),
                anyString(), anyString(), eq(5)
        );
        verify(mockLedgerService, times(1)).recordMovement(
                eq(mockConn), eq(3), eq(1), eq(10), eq("CHUYEN_NHAN"),
                eq(25), eq(25), isNull(), eq("TRANSFER"), eq(88L),
                anyString(), anyString(), eq(5)
        );
    }

    /**
     * Khi cập nhật tồn kho thất bại (VD: thiếu tồn kho), transaction phải rollback toàn bộ.
     */
    @Test
    public void createTransfer_shouldRollback_whenStockUpdateFails() throws Exception {
        StockTransferItem item = new StockTransferItem();
        item.setDrugId(1);
        item.setLotId(10);
        item.setQuantity(500);

        when(mockRepo.insertTransfer(eq(mockConn), any())).thenReturn(89L);
        when(mockRepo.upsertLotStock(eq(mockConn), eq(2), eq(1), eq(10), eq(-500)))
                .thenThrow(new IllegalStateException("Không đủ tồn kho"));

        CompletionStage<StockTransferDto> stage = service.createTransfer(
                2, 3, "Chuyển vượt tồn", 5, List.of(item)
        );

        try {
            stage.toCompletableFuture().get();
            fail("Phải ném ngoại lệ khi không đủ tồn kho");
        } catch (Exception expected) {
            // Đảm bảo transaction được rollback và không commit
            verify(mockConn, times(1)).rollback();
            verify(mockConn, never()).commit();
        }
    }

    /**
     * Không cho phép chuyển kho khi chi nhánh nguồn trùng với chi nhánh đích.
     */
    @Test(expected = IllegalArgumentException.class)
    public void createTransfer_shouldThrowException_whenSourceEqualsTargetBranch() {
        StockTransferItem item = new StockTransferItem();
        item.setDrugId(1);
        item.setLotId(10);
        item.setQuantity(10);

        service.createTransfer(2, 2, "Trùng chi nhánh", 1, List.of(item));
    }

    /**
     * Không cho phép tạo phiếu chuyển kho khi danh sách dòng thuốc rỗng hoặc số lượng <= 0.
     */
    @Test(expected = IllegalArgumentException.class)
    public void createTransfer_shouldThrowException_whenItemsEmpty() {
        service.createTransfer(2, 3, "Thiếu dòng thuốc", 1, Collections.emptyList());
    }

    /**
     * Kiểm tra tra cứu danh sách phiếu chuyển kho phân trang qua PagedResponse.
     */
    @Test
    public void listTransfers_shouldReturnPagedResponse() throws Exception {
        PagedResponse expected = new PagedResponse(Collections.emptyList(), 1, 20, 0L);
        when(mockRepo.findTransfers(eq(2), isNull(), isNull(), eq(1), eq(20), eq("DESC")))
                .thenReturn(expected);

        PagedResponse actual = service.listTransfers(2, null, null, 1, 20, "createdAt,desc")
                .toCompletableFuture().get();

        assertEquals(1, actual.getPage());
        assertEquals(20, actual.getSize());
        assertEquals(0L, actual.getTotal());
    }

    /**
     * Kiểm tra lấy chi tiết phiếu chuyển kho theo ID hợp lệ.
     */
    @Test
    public void getTransferById_shouldReturnTransfer_whenExists() throws Exception {
        StockTransferDto dto = new StockTransferDto();
        dto.id = 10L;
        dto.transferCode = "TF-20261126000001";
        when(mockRepo.findById(10L)).thenReturn(Optional.of(dto));

        Optional<StockTransferDto> result = service.getTransferById(10L).toCompletableFuture().get();
        assertTrue(result.isPresent());
        assertEquals("TF-20261126000001", result.get().transferCode);
    }
}
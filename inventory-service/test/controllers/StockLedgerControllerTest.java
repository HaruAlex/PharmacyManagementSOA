package controllers;

import models.PagedResponse;
import org.junit.Before;
import org.junit.Test;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import services.StockLedgerService;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho StockLedgerController (BE-30 / FR-KH-03).
 * Kiểm thử kiểm tra header X-Branch-Id, phân quyền RBAC (WH, AD) và phản hồi HTTP chuẩn.
 */
public class StockLedgerControllerTest {

    private StockLedgerService mockService;
    private StockLedgerController controller;

    @Before
    public void setUp() {
        mockService = mock(StockLedgerService.class);
        controller = new StockLedgerController(mockService);
    }

    /**
     * Khi hợp lệ (role WH, có X-Branch-Id, phân trang hợp lệ) phải trả về HTTP 200 OK.
     */
    @Test
    public void getLedger_shouldReturn200_whenRequestValidForWarehouseStaff() {
        PagedResponse emptyPage = new PagedResponse(Collections.emptyList(), 1, 20, 0L);
        when(mockService.getStockLedger(
                eq(2), eq(1), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20), eq("createdAt,desc")
        )).thenReturn(CompletableFuture.completedFuture(emptyPage));

        Http.RequestBuilder requestBuilder = Helpers.fakeRequest()
                .method("GET")
                .uri("/api/v1/inventory/ledger?drugId=1&page=1&size=20")
                .header("X-User-Role", "WH")
                .header("X-Branch-Id", "2")
                .header("Authorization", "Bearer dummy-token");

        Result result = controller.getLedger(
                requestBuilder.build(), 1, null, null, null, null, 1, 20, "createdAt,desc"
        ).toCompletableFuture().join();

        assertEquals(Http.Status.OK, result.status());
        assertTrue(result.contentType().orElse("").contains("application/json"));
    }

    /**
     * Nhân viên bán hàng (SP) không có quyền xem thẻ kho -> trả về HTTP 403 Forbidden.
     */
    @Test
    public void getLedger_shouldReturn403_whenRoleIsNotAllowed() {
        Http.RequestBuilder requestBuilder = Helpers.fakeRequest()
                .method("GET")
                .uri("/api/v1/inventory/ledger")
                .header("X-User-Role", "SP")
                .header("X-Branch-Id", "2");

        Result result = controller.getLedger(
                requestBuilder.build(), null, null, null, null, null, 1, 20, "createdAt,desc"
        ).toCompletableFuture().join();

        assertEquals(Http.Status.FORBIDDEN, result.status());
        verifyNoInteractions(mockService);
    }

    /**
     * Thiếu header X-Branch-Id -> trả về HTTP 400 Bad Request.
     */
    @Test
    public void getLedger_shouldReturn400_whenBranchHeaderMissing() {
        Http.RequestBuilder requestBuilder = Helpers.fakeRequest()
                .method("GET")
                .uri("/api/v1/inventory/ledger")
                .header("X-User-Role", "AD");

        Result result = controller.getLedger(
                requestBuilder.build(), null, null, null, null, null, 1, 20, "createdAt,desc"
        ).toCompletableFuture().join();

        assertEquals(Http.Status.BAD_REQUEST, result.status());
        verifyNoInteractions(mockService);
    }

    /**
     * Tham số phân trang size vượt quá giới hạn (VD: size = 500) -> trả về HTTP 400 Bad Request.
     */
    @Test
    public void getLedger_shouldReturn400_whenPaginationInvalid() {
        Http.RequestBuilder requestBuilder = Helpers.fakeRequest()
                .method("GET")
                .uri("/api/v1/inventory/ledger?page=1&size=500")
                .header("X-User-Role", "WH")
                .header("X-Branch-Id", "2");

        Result result = controller.getLedger(
                requestBuilder.build(), null, null, null, null, null, 1, 500, "createdAt,desc"
        ).toCompletableFuture().join();

        assertEquals(Http.Status.BAD_REQUEST, result.status());
        verifyNoInteractions(mockService);
    }
}
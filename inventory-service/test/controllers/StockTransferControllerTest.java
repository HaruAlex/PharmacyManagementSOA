package controllers;

import models.PagedResponse;
import models.StockTransferDto;
import org.junit.Before;
import org.junit.Test;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import services.StockTransferService;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho StockTransferController (BE-31 / Stock Transfers).
 * Kiểm thử phân quyền RBAC (WH, AD), header X-Branch-Id, kiểm tra payload và mã trạng thái HTTP.
 */
public class StockTransferControllerTest {

    private StockTransferService mockService;
    private StockTransferController controller;

    @Before
    public void setUp() {
        mockService = mock(StockTransferService.class);
        controller = new StockTransferController(mockService);
    }

    /**
     * Tạo phiếu chuyển kho hợp lệ bởi nhân viên kho (WH) -> trả về HTTP 201 Created.
     */
    @Test
    public void createTransfer_shouldReturn201_whenPayloadValid() {
        StockTransferDto createdDto = new StockTransferDto();
        createdDto.id = 1L;
        createdDto.transferCode = "TF-20261126210000";
        createdDto.sourceBranchId = 2;
        createdDto.targetBranchId = 3;
        createdDto.status = "COMPLETED";

        when(mockService.createTransfer(eq(2), eq(3), anyString(), eq(5), anyList()))
                .thenReturn(CompletableFuture.completedFuture(createdDto));

        String jsonBody = "{"
                + "\"targetBranchId\": 3,"
                + "\"note\": \"Chuyển bổ sung lô thuốc\","
                + "\"createdBy\": 5,"
                + "\"items\": [{\"drugId\": 1, \"lotId\": 10, \"quantity\": 20}]"
                + "}";

        Http.RequestBuilder req = Helpers.fakeRequest()
                .method("POST")
                .uri("/api/v1/inventory/transfers")
                .header("X-User-Role", "WH")
                .header("X-Branch-Id", "2")
                .bodyJson(Json.parse(jsonBody));

        Result result = controller.createTransfer(req.build()).toCompletableFuture().join();

        assertEquals(Http.Status.CREATED, result.status());
        assertTrue(result.contentType().orElse("").contains("application/json"));
    }

    /**
     * Vai trò không được phép (VD: SP) -> trả về HTTP 403 Forbidden.
     */
    @Test
    public void listTransfers_shouldReturn403_whenRoleForbidden() {
        Http.RequestBuilder req = Helpers.fakeRequest()
                .method("GET")
                .uri("/api/v1/inventory/transfers")
                .header("X-User-Role", "SP")
                .header("X-Branch-Id", "2");

        Result result = controller.listTransfers(req.build(), null, null, 1, 20, "createdAt,desc")
                .toCompletableFuture().join();

        assertEquals(Http.Status.FORBIDDEN, result.status());
        verifyNoInteractions(mockService);
    }

    /**
     * Thiếu header X-Branch-Id -> trả về HTTP 400 Bad Request.
     */
    @Test
    public void listTransfers_shouldReturn400_whenMissingBranchId() {
        Http.RequestBuilder req = Helpers.fakeRequest()
                .method("GET")
                .uri("/api/v1/inventory/transfers")
                .header("X-User-Role", "WH");

        Result result = controller.listTransfers(req.build(), null, null, 1, 20, "createdAt,desc")
                .toCompletableFuture().join();

        assertEquals(Http.Status.BAD_REQUEST, result.status());
        verifyNoInteractions(mockService);
    }

    /**
     * Truy vấn danh sách hợp lệ -> trả về HTTP 200 OK.
     */
    @Test
    public void listTransfers_shouldReturn200_whenRequestValid() {
        when(mockService.listTransfers(eq(2), isNull(), isNull(), eq(1), eq(20), eq("createdAt,desc")))
                .thenReturn(CompletableFuture.completedFuture(
                        new PagedResponse(Collections.emptyList(), 1, 20, 0L)
                ));

        Http.RequestBuilder req = Helpers.fakeRequest()
                .method("GET")
                .uri("/api/v1/inventory/transfers?page=1&size=20")
                .header("X-User-Role", "AD")
                .header("X-Branch-Id", "2");

        Result result = controller.listTransfers(req.build(), null, null, 1, 20, "createdAt,desc")
                .toCompletableFuture().join();

        assertEquals(Http.Status.OK, result.status());
        assertTrue(result.contentType().orElse("").contains("application/json"));
    }

    /**
     * Không tìm thấy phiếu chuyển kho theo ID -> trả về HTTP 404 Not Found.
     */
    @Test
    public void getTransferById_shouldReturn404_whenNotFound() {
        when(mockService.getTransferById(999L))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        Http.RequestBuilder req = Helpers.fakeRequest()
                .method("GET")
                .uri("/api/v1/inventory/transfers/999")
                .header("X-User-Role", "WH")
                .header("X-Branch-Id", "2");

        Result result = controller.getTransferById(req.build(), 999L).toCompletableFuture().join();

        assertEquals(Http.Status.NOT_FOUND, result.status());
    }
}
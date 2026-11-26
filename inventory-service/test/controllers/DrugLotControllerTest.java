package controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.NOT_FOUND;
import static play.mvc.Http.Status.OK;

import org.junit.Before;
import org.junit.Test;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import repositories.InventoryRepository;
import services.DrugLotService;

/**
 * Bộ unit test kiểm thử tầng HTTP của DrugLotController cho tính năng BE-29:
 * - Kiểm tra bắt buộc và hợp lệ của header X-Branch-Id
 * - Truyền và phản hồi header X-Request-Id
 * - Xử lý lỗi tham số phân trang (400 INVALID_PARAMETER)
 * - Trả về chi tiết lô thuốc (200 OK) hoặc không tìm thấy (404 LOT_NOT_FOUND)
 */
public class DrugLotControllerTest {

    // Controller cần kiểm thử.
    private DrugLotController controller;

    /**
     * Khởi tạo controller với DrugLotService trước mỗi test case.
     */
    @Before
    public void setUp() {
        DrugLotService service = new DrugLotService(new InventoryRepository());
        this.controller = new DrugLotController(service);
    }

    /**
     * Kiểm tra thiếu header X-Branch-Id phải trả về HTTP 400 kèm mã lỗi INVALID_BRANCH_HEADER
     * và tự sinh header X-Request-Id.
     */
    @Test
    public void getLots_shouldReturn400WhenBranchHeaderMissing() {
        Http.Request request = Helpers.fakeRequest("GET", "/api/drug-lots").build();

        Result result = controller.getLots(request, null, null, 0, 10, null);

        assertEquals(BAD_REQUEST, result.status());
        assertNotNull(result.header("X-Request-Id").orElse(null));
        assertEquals(true, Helpers.contentAsString(result).contains("INVALID_BRANCH_HEADER"));
    }

    /**
     * Kiểm tra header X-Branch-Id không hợp lệ (ví dụ số âm) phải trả về HTTP 400
     * và giữ nguyên giá trị X-Request-Id gửi lên.
     */
    @Test
    public void getLots_shouldReturn400WhenBranchHeaderIsInvalid() {
        Http.Request request =
                Helpers.fakeRequest("GET", "/api/drug-lots")
                        .header("X-Branch-Id", "-5")
                        .header("X-Request-Id", "req-invalid-branch")
                        .build();

        Result result = controller.getLots(request, null, null, 0, 10, null);

        assertEquals(BAD_REQUEST, result.status());
        assertEquals("req-invalid-branch", result.header("X-Request-Id").orElse(null));
    }

    /**
     * Kiểm tra request hợp lệ trả về HTTP 200, giữ đúng X-Request-Id và chứa dữ liệu lô thuốc.
     */
    @Test
    public void getLots_shouldReturn200WithMatchingRequestId() {
        Http.Request request =
                Helpers.fakeRequest("GET", "/api/drug-lots?page=0&size=10&sort=expiryDate")
                        .header("X-Branch-Id", "1")
                        .header("X-Request-Id", "req-be29-ok")
                        .build();

        Result result = controller.getLots(request, null, null, 0, 10, "expiryDate");

        assertEquals(OK, result.status());
        assertEquals("req-be29-ok", result.header("X-Request-Id").orElse(null));
        assertEquals(true, Helpers.contentAsString(result).contains("LOT-VITC-01"));
    }

    /**
     * Kiểm tra khi service báo IllegalArgumentException do tham số phân trang sai,
     * controller phải trả về HTTP 400 với mã lỗi INVALID_PARAMETER.
     */
    @Test
    public void getLots_shouldReturn400WhenPaginationInvalid() {
        Http.Request request =
                Helpers.fakeRequest("GET", "/api/drug-lots?page=0&size=-1")
                        .header("X-Branch-Id", "1")
                        .build();

        Result result = controller.getLots(request, null, null, 0, -1, null);

        assertEquals(BAD_REQUEST, result.status());
        assertEquals(true, Helpers.contentAsString(result).contains("INVALID_PARAMETER"));
    }

    /**
     * Kiểm tra endpoint chi tiết lô thuốc trả về 200 khi ID tồn tại
     * và 404 kèm mã lỗi LOT_NOT_FOUND khi ID không tồn tại.
     */
    @Test
    public void getLotDetail_shouldReturn200WhenExistsAnd404WhenNotFound() {
        Http.Request okRequest =
                Helpers.fakeRequest("GET", "/api/drug-lots/1")
                        .header("X-Branch-Id", "1")
                        .build();
        Result okResult = controller.getLotDetail(okRequest, 1L);
        assertEquals(OK, okResult.status());

        Http.Request notFoundRequest =
                Helpers.fakeRequest("GET", "/api/drug-lots/999")
                        .header("X-Branch-Id", "1")
                        .build();
        Result notFoundResult = controller.getLotDetail(notFoundRequest, 999L);
        assertEquals(NOT_FOUND, notFoundResult.status());
        assertEquals(true, Helpers.contentAsString(notFoundResult).contains("LOT_NOT_FOUND"));
    }
}
package controllers;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import models.DrugLotDto;
import models.ErrorResponse;
import models.PagedResponse;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.DrugLotService;

/**
 * Controller xử lý các endpoint HTTP liên quan đến danh sách và chi tiết lô thuốc
 * theo chi nhánh hiện hành (Play Framework).
 */
public class DrugLotController extends Controller {

    // Dịch vụ nghiệp vụ lô thuốc.
    private final DrugLotService drugLotService;

    /**
     * Khởi tạo controller thông qua Dependency Injection.
     */
    @Inject
    public DrugLotController(DrugLotService drugLotService) {
        this.drugLotService = drugLotService;
    }

    /**
     * Lấy danh sách lô thuốc có lọc, phân trang và sắp xếp theo chi nhánh từ header X-Branch-Id.
     */
    public Result getLots(
            Http.Request request, Long drugId, String status, int page, int size, String sort) {
        String requestId = resolveRequestId(request);
        Optional<Long> branchId = extractBranchId(request);

        // Kiểm tra header X-Branch-Id bắt buộc trước khi gọi service.
        if (branchId.isEmpty()) {
            return badRequest(
                            Json.toJson(
                                    new ErrorResponse(
                                            "INVALID_BRANCH_HEADER",
                                            "Thiếu hoặc sai định dạng X-Branch-Id",
                                            Map.of())))
                    .withHeader("X-Request-Id", requestId);
        }

        // Gọi service truy vấn dữ liệu và ánh xạ ngoại lệ sang mã lỗi HTTP tương ứng.
        try {
            PagedResponse data =
                    drugLotService.getLots(branchId.get(), drugId, status, page, size, sort);
            return ok(Json.toJson(data)).withHeader("X-Request-Id", requestId);
        } catch (IllegalArgumentException ex) {
            return badRequest(
                            Json.toJson(
                                    new ErrorResponse("INVALID_PARAMETER", ex.getMessage(), Map.of())))
                    .withHeader("X-Request-Id", requestId);
        } catch (Exception ex) {
            return internalServerError(
                            Json.toJson(
                                    new ErrorResponse("INTERNAL_ERROR", ex.getMessage(), Map.of())))
                    .withHeader("X-Request-Id", requestId);
        }
    }

    /**
     * Lấy thông tin chi tiết của một lô thuốc theo ID trong phạm vi chi nhánh hiện hành.
     */
    public Result getLotDetail(Http.Request request, Long id) {
        String requestId = resolveRequestId(request);
        Optional<Long> branchId = extractBranchId(request);

        // Từ chối yêu cầu nếu thiếu hoặc sai định dạng ID chi nhánh.
        if (branchId.isEmpty()) {
            return badRequest(
                            Json.toJson(
                                    new ErrorResponse(
                                            "INVALID_BRANCH_HEADER",
                                            "Thiếu hoặc sai định dạng X-Branch-Id",
                                            Map.of())))
                    .withHeader("X-Request-Id", requestId);
        }

        try {
            Optional<DrugLotDto> lot = drugLotService.getLotDetail(id, branchId.get());
            // Trả về 404 nếu không tìm thấy lô thuốc thuộc chi nhánh này.
            if (lot.isEmpty()) {
                return notFound(
                                Json.toJson(
                                        new ErrorResponse(
                                                "LOT_NOT_FOUND",
                                                "Không tìm thấy lô thuốc tại chi nhánh hiện hành",
                                                Map.of("lotId", id, "branchId", branchId.get()))))
                        .withHeader("X-Request-Id", requestId);
            }
            return ok(Json.toJson(lot.get())).withHeader("X-Request-Id", requestId);
        } catch (Exception ex) {
            return internalServerError(
                            Json.toJson(
                                    new ErrorResponse("INTERNAL_ERROR", ex.getMessage(), Map.of())))
                    .withHeader("X-Request-Id", requestId);
        }
    }

    /**
     * Trích xuất và kiểm tra ID chi nhánh hợp lệ (số nguyên dương) từ header X-Branch-Id.
     */
    private Optional<Long> extractBranchId(Http.Request request) {
        return request.header("X-Branch-Id")
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .flatMap(
                        s -> {
                            try {
                                long id = Long.parseLong(s);
                                return id > 0 ? Optional.of(id) : Optional.empty();
                            } catch (NumberFormatException e) {
                                return Optional.empty();
                            }
                        });
    }

    /**
     * Lấy X-Request-Id từ request nếu có; nếu không có thì sinh UUID mới để theo vết log.
     */
    private String resolveRequestId(Http.Request request) {
        return request.header("X-Request-Id")
                .filter(s -> !s.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
    }
}
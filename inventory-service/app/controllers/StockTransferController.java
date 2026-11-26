package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.ErrorResponse;
import models.StockTransferItem;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.StockTransferService;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Tầng Controller xử lý các endpoint API chuyển kho (BE-31 / Stock Transfers):
 * - POST /api/v1/inventory/transfers
 * - GET  /api/v1/inventory/transfers
 * - GET  /api/v1/inventory/transfers/:id
 */
public class StockTransferController extends Controller {

    // Chỉ Nhân viên kho (WH) và Admin (AD) được phép thao tác chuyển kho
    private static final Set<String> ALLOWED_ROLES = Set.of("WH", "AD");
    private final StockTransferService transferService;

    @Inject
    public StockTransferController(StockTransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * POST /api/v1/inventory/transfers
     * Body JSON mẫu:
     * {
     *   "targetBranchId": 3,
     *   "note": "Điều chuyển bổ sung tồn kho",
     *   "createdBy": 2,
     *   "items": [ { "drugId": 1, "lotId": 10, "quantity": 20 } ]
     * }
     */
    public CompletionStage<Result> createTransfer(Http.Request request) {
        Result authOrBranchError = validateRoleAndBranch(request);
        if (authOrBranchError != null) {
            return CompletableFuture.completedFuture(authOrBranchError);
        }

        int sourceBranchId = Integer.parseInt(request.header("X-Branch-Id").get().trim());
        JsonNode body = request.body().asJson();
        if (body == null || !body.has("targetBranchId")) {
            return CompletableFuture.completedFuture(
                    badRequest(Json.toJson(new ErrorResponse(
                            "INVALID_PAYLOAD",
                            "Thiếu dữ liệu JSON hoặc trường targetBranchId",
                            Collections.emptyMap()
                    )))
            );
        }

        try {
            int targetBranchId = body.get("targetBranchId").asInt(-1);
            String note = body.hasNonNull("note") ? body.get("note").asText() : null;
            int userId = parseUserId(request, body);

            List<StockTransferItem> items = new ArrayList<>();
            if (body.has("items") && body.get("items").isArray()) {
                for (JsonNode node : body.get("items")) {
                    StockTransferItem item = new StockTransferItem();
                    if (node.hasNonNull("drugId")) item.setDrugId(node.get("drugId").asInt());
                    if (node.hasNonNull("lotId")) item.setLotId(node.get("lotId").asInt());
                    if (node.hasNonNull("quantity")) item.setQuantity(node.get("quantity").asInt());
                    items.add(item);
                }
            }

            return transferService.createTransfer(sourceBranchId, targetBranchId, note, userId, items)
                    .thenApply(created -> created(Json.toJson(created)))
                    .exceptionally(ex -> handleException(ex));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                    badRequest(Json.toJson(new ErrorResponse(
                            "INVALID_PARAMETER",
                            e.getMessage(),
                            Collections.emptyMap()
                    )))
            );
        }
    }

    /**
     * GET /api/v1/inventory/transfers
     * Trả về danh sách phiếu chuyển kho (Stock Transfers) có phân trang.
     */
    public CompletionStage<Result> listTransfers(
            Http.Request request,
            Integer targetBranchId,
            String status,
            int page,
            int size,
            String sort
    ) {
        Result authOrBranchError = validateRoleAndBranch(request);
        if (authOrBranchError != null) {
            return CompletableFuture.completedFuture(authOrBranchError);
        }

        if (page < 1 || size < 1 || size > 100) {
            return CompletableFuture.completedFuture(
                    badRequest(Json.toJson(new ErrorResponse(
                            "INVALID_PAGINATION",
                            "Tham số page >= 1 và 1 <= size <= 100",
                            Map.of("page", page, "size", size)
                    )))
            );
        }

        int branchId = Integer.parseInt(request.header("X-Branch-Id").get().trim());
        try {
            return transferService.listTransfers(branchId, targetBranchId, status, page, size, sort)
                    .thenApply(paged -> ok(Json.toJson(paged)))
                    .exceptionally(ex -> handleException(ex));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                    badRequest(Json.toJson(new ErrorResponse(
                            "INVALID_PARAMETER",
                            e.getMessage(),
                            Collections.emptyMap()
                    )))
            );
        }
    }

    /**
     * GET /api/v1/inventory/transfers/:id
     * Trả về chi tiết một phiếu chuyển kho theo ID.
     */
    public CompletionStage<Result> getTransferById(Http.Request request, long id) {
        Result authOrBranchError = validateRoleAndBranch(request);
        if (authOrBranchError != null) {
            return CompletableFuture.completedFuture(authOrBranchError);
        }

        try {
            return transferService.getTransferById(id)
                    .thenApply(opt -> opt
                            .<Result>map(dto -> ok(Json.toJson(dto)))
                            .orElseGet(() -> notFound(Json.toJson(new ErrorResponse(
                                    "TRANSFER_NOT_FOUND",
                                    "Không tìm thấy phiếu chuyển kho với id = " + id,
                                    Collections.emptyMap()
                            )))))
                    .exceptionally(ex -> handleException(ex));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                    badRequest(Json.toJson(new ErrorResponse(
                            "INVALID_PARAMETER",
                            e.getMessage(),
                            Collections.emptyMap()
                    )))
            );
        }
    }

    /**
     * Kiểm tra quyền RBAC (WH, AD) và header bắt buộc X-Branch-Id.
     */
    private Result validateRoleAndBranch(Http.Request request) {
        Optional<String> roleOpt = request.header("X-User-Role");
        if (roleOpt.isEmpty() || !ALLOWED_ROLES.contains(roleOpt.get().trim().toUpperCase())) {
            return forbidden(Json.toJson(new ErrorResponse(
                    "FORBIDDEN",
                    "Chỉ Nhân viên kho (WH) hoặc Admin (AD) mới được thao tác chuyển kho",
                    Collections.emptyMap()
            )));
        }

        Optional<String> branchOpt = request.header("X-Branch-Id");
        if (branchOpt.isEmpty() || branchOpt.get().isBlank()) {
            return badRequest(Json.toJson(new ErrorResponse(
                    "MISSING_BRANCH_ID",
                    "Thiếu header X-Branch-Id cho chi nhánh hiện hành",
                    Collections.emptyMap()
            )));
        }

        try {
            Integer.parseInt(branchOpt.get().trim());
        } catch (NumberFormatException e) {
            return badRequest(Json.toJson(new ErrorResponse(
                    "INVALID_BRANCH_ID",
                    "Header X-Branch-Id phải là số nguyên hợp lệ",
                    Map.of("X-Branch-Id", branchOpt.get())
            )));
        }

        return null;
    }

    /**
     * Lấy userId từ header X-User-Id (nếu có) hoặc body.createdBy, mặc định 1 khi test.
     */
    private int parseUserId(Http.Request request, JsonNode body) {
        Optional<String> headerUser = request.header("X-User-Id");
        if (headerUser.isPresent() && !headerUser.get().isBlank()) {
            try {
                return Integer.parseInt(headerUser.get().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (body.hasNonNull("createdBy")) {
            return body.get("createdBy").asInt(1);
        }
        return 1;
    }

    private Result handleException(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof IllegalArgumentException) {
            return badRequest(Json.toJson(new ErrorResponse(
                    "INVALID_PARAMETER",
                    cause.getMessage(),
                    Collections.emptyMap()
            )));
        }
        return internalServerError(Json.toJson(new ErrorResponse(
                "INTERNAL_ERROR",
                "Lỗi hệ thống khi xử lý phiếu chuyển kho",
                Collections.emptyMap()
        )));
    }
}
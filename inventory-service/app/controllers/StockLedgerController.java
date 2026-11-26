package controllers;

import models.ErrorResponse;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.StockLedgerService;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Tầng Controller tiếp nhận HTTP request tra cứu thẻ kho (FR-KH-03 / BE-30),
 * kiểm tra quyền RBAC, header X-Branch-Id, tham số phân trang và trả lỗi chuẩn hóa.
 */
public class StockLedgerController extends Controller {

    // Chỉ vai trò Nhân viên kho (WH) và Admin (AD) được phép xem thẻ kho
    private static final Set<String> ALLOWED_ROLES = Set.of("WH", "AD");
    private final StockLedgerService ledgerService;

    @Inject
    public StockLedgerController(StockLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /**
     * GET /api/v1/inventory/ledger
     * Hỗ trợ lọc theo drugId, lotId, type, from, to và phân trang page, size, sort.
     */
    public CompletionStage<Result> getLedger(
            Http.Request request,
            Integer drugId,
            Integer lotId,
            String type,
            String from,
            String to,
            int page,
            int size,
            String sort
    ) {
        // 1. Kiểm tra phân quyền vai trò (RBAC: chỉ WH và AD)
        Optional<String> roleOpt = request.header("X-User-Role");
        if (roleOpt.isEmpty() || !ALLOWED_ROLES.contains(roleOpt.get().trim().toUpperCase())) {
            return CompletableFuture.completedFuture(
                    forbidden(Json.toJson(new ErrorResponse(
                            "FORBIDDEN",
                            "Chỉ Nhân viên kho (WH) hoặc Admin (AD) mới được xem thẻ kho",
                            Collections.emptyMap()
                    )))
            );
        }

        // 2. Kiểm tra header bắt buộc X-Branch-Id (chi nhánh hiện hành)
        Optional<String> branchHeader = request.header("X-Branch-Id");
        if (branchHeader.isEmpty() || branchHeader.get().isBlank()) {
            return CompletableFuture.completedFuture(
                    badRequest(Json.toJson(new ErrorResponse(
                            "MISSING_BRANCH_ID",
                            "Thiếu header X-Branch-Id cho chi nhánh hiện hành",
                            Collections.emptyMap()
                    )))
            );
        }

        // Chuyển đổi X-Branch-Id sang số nguyên
        int branchId;
        try {
            branchId = Integer.parseInt(branchHeader.get().trim());
        } catch (NumberFormatException e) {
            return CompletableFuture.completedFuture(
                    badRequest(Json.toJson(new ErrorResponse(
                            "INVALID_BRANCH_ID",
                            "Header X-Branch-Id phải là số nguyên hợp lệ",
                            Map.of("X-Branch-Id", branchHeader.get())
                    )))
            );
        }

        // 3. Kiểm tra giới hạn tham số phân trang
        if (page < 1 || size < 1 || size > 100) {
            return CompletableFuture.completedFuture(
                    badRequest(Json.toJson(new ErrorResponse(
                            "INVALID_PAGINATION",
                            "Tham số page >= 1 và 1 <= size <= 100",
                            Map.of("page", page, "size", size)
                    )))
            );
        }

        // 4. Gọi Service truy vấn thẻ kho và chuẩn hóa phản hồi lỗi
        try {
            return ledgerService.getStockLedger(branchId, drugId, lotId, type, from, to, page, size, sort)
                    .thenApply(pagedResult -> ok(Json.toJson(pagedResult)))
                    .exceptionally(ex -> {
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
                                "Lỗi hệ thống khi truy xuất thẻ kho",
                                Collections.emptyMap()
                        )));
                    });
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
}
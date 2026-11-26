package services;

import models.PagedResponse;
import models.StockTransfer;
import models.StockTransferDto;
import models.StockTransferItem;
import play.libs.concurrent.HttpExecutionContext;
import repositories.StockTransferRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Tầng Service xử lý nghiệp vụ chuyển kho giữa các chi nhánh (BE-31 / Stock Transfers),
 * đảm bảo trừ/cộng tồn kho theo lô và ghi biến động thẻ kho CHUYEN_XUAT / CHUYEN_NHAN
 * trong cùng một giao dịch (transaction).
 */
@Singleton
public class StockTransferService {

    private static final DateTimeFormatter CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final StockTransferRepository transferRepository;
    private final StockLedgerService ledgerService;
    private final HttpExecutionContext httpExecutionContext;

    @Inject
    public StockTransferService(
            StockTransferRepository transferRepository,
            StockLedgerService ledgerService,
            HttpExecutionContext httpExecutionContext
    ) {
        this.transferRepository = transferRepository;
        this.ledgerService = ledgerService;
        this.httpExecutionContext = httpExecutionContext;
    }

    /**
     * Tạo phiếu chuyển kho từ chi nhánh nguồn sang chi nhánh đích và ghi nhận thẻ kho 2 đầu.
     */
    public CompletionStage<StockTransferDto> createTransfer(
            int sourceBranchId,
            int targetBranchId,
            String note,
            int userId,
            List<StockTransferItem> items
    ) {
        // Kiểm tra ràng buộc nghiệp vụ trước khi mở transaction
        if (targetBranchId <= 0) {
            throw new IllegalArgumentException("Chi nhánh nhận (targetBranchId) không hợp lệ");
        }
        if (sourceBranchId == targetBranchId) {
            throw new IllegalArgumentException("Chi nhánh xuất và chi nhánh nhận phải khác nhau");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Phiếu chuyển kho phải có ít nhất 1 dòng thuốc");
        }
        for (StockTransferItem item : items) {
            if (item.getDrugId() == null || item.getDrugId() <= 0) {
                throw new IllegalArgumentException("Mã thuốc (drugId) không hợp lệ");
            }
            if (item.getLotId() == null || item.getLotId() <= 0) {
                throw new IllegalArgumentException("Mã lô thuốc (lotId) không hợp lệ");
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Số lượng chuyển phải lớn hơn 0");
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = transferRepository.getDatabase().getConnection()) {
                boolean prevAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                try {
                    LocalDateTime now = LocalDateTime.now();
                    String transferCode = "TF-" + now.format(CODE_FORMATTER);

                    StockTransfer transfer = new StockTransfer();
                    transfer.setTransferCode(transferCode);
                    transfer.setSourceBranchId(sourceBranchId);
                    transfer.setTargetBranchId(targetBranchId);
                    transfer.setStatus("COMPLETED");
                    transfer.setNote(note);
                    transfer.setCreatedBy(userId);
                    transfer.setCreatedAt(now);
                    transfer.setItems(items);

                    // 1. Lưu phiếu chuyển kho và danh sách dòng chi tiết
                    Long transferId = transferRepository.insertTransfer(conn, transfer);

                    // 2. Xử lý từng lô thuốc: trừ tồn chi nhánh xuất, cộng tồn chi nhánh nhận, ghi thẻ kho
                    for (StockTransferItem item : items) {
                        int qty = item.getQuantity();

                        int sourceBalanceAfter = transferRepository.upsertLotStock(
                                conn, sourceBranchId, item.getDrugId(), item.getLotId(), -qty
                        );
                        int targetBalanceAfter = transferRepository.upsertLotStock(
                                conn, targetBranchId, item.getDrugId(), item.getLotId(), qty
                        );

                        // Ghi biến động xuất chuyển cho chi nhánh nguồn (số lượng âm)
                        ledgerService.recordMovement(
                                conn,
                                sourceBranchId,
                                item.getDrugId(),
                                item.getLotId(),
                                "CHUYEN_XUAT",
                                -qty,
                                sourceBalanceAfter,
                                null,
                                "TRANSFER",
                                transferId,
                                transferCode,
                                note != null ? note : "Chuyển kho sang chi nhánh #" + targetBranchId,
                                userId
                        );

                        // Ghi biến động nhận chuyển cho chi nhánh đích (số lượng dương)
                        ledgerService.recordMovement(
                                conn,
                                targetBranchId,
                                item.getDrugId(),
                                item.getLotId(),
                                "CHUYEN_NHAN",
                                qty,
                                targetBalanceAfter,
                                null,
                                "TRANSFER",
                                transferId,
                                transferCode,
                                note != null ? note : "Nhận chuyển kho từ chi nhánh #" + sourceBranchId,
                                userId
                        );
                    }

                    conn.commit();
                    conn.setAutoCommit(prevAutoCommit);

                    StockTransferDto dto = new StockTransferDto();
                    dto.id = transferId;
                    dto.transferCode = transferCode;
                    dto.sourceBranchId = sourceBranchId;
                    dto.targetBranchId = targetBranchId;
                    dto.status = transfer.getStatus();
                    dto.note = note;
                    dto.createdBy = userId;
                    dto.createdAt = now;
                    dto.items = items;
                    return dto;
                } catch (Exception ex) {
                    // Rollback toàn bộ nếu có bất kỳ lỗi tồn kho hoặc ghi thẻ kho nào
                    try {
                        conn.rollback();
                        conn.setAutoCommit(prevAutoCommit);
                    } catch (SQLException ignored) {
                    }
                    if (ex instanceof IllegalStateException) {
                        throw new IllegalArgumentException(ex.getMessage(), ex);
                    }
                    throw new RuntimeException("Lỗi khi xử lý giao dịch chuyển kho", ex);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Lỗi kết nối cơ sở dữ liệu khi chuyển kho", e);
            }
        }, httpExecutionContext.current());
    }

    /**
     * Tra cứu danh sách phiếu chuyển kho (Stock Transfers) có phân trang.
     */
    public CompletionStage<PagedResponse> listTransfers(
            int branchId,
            Integer targetBranchId,
            String status,
            int page,
            int size,
            String sort
    ) {
        String sortOrder = "DESC";
        if (sort != null && sort.contains(",")) {
            String[] parts = sort.split(",");
            if (parts.length > 1) {
                sortOrder = parts[1].trim().toUpperCase();
            }
        }

        final String finalSortOrder = sortOrder;
        return CompletableFuture.supplyAsync(() -> {
            try {
                return transferRepository.findTransfers(
                        branchId, targetBranchId, status, page, size, finalSortOrder
                );
            } catch (SQLException e) {
                throw new RuntimeException("Lỗi truy vấn danh sách chuyển kho", e);
            }
        }, httpExecutionContext.current());
    }

    /**
     * Lấy chi tiết một phiếu chuyển kho theo ID.
     */
    public CompletionStage<Optional<StockTransferDto>> getTransferById(long transferId) {
        if (transferId <= 0) {
            throw new IllegalArgumentException("ID phiếu chuyển kho không hợp lệ");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return transferRepository.findById(transferId);
            } catch (SQLException e) {
                throw new RuntimeException("Lỗi truy vấn chi tiết phiếu chuyển kho", e);
            }
        }, httpExecutionContext.current());
    }
}
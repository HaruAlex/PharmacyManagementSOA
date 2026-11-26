package services;

import models.PagedResponse;
import models.StockLedger;
import play.libs.concurrent.HttpExecutionContext;
import repositories.StockLedgerRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Tầng Service xử lý quy tắc nghiệp vụ thẻ kho (FR-KH-03 / BE-30)
 * và thực thi bất đồng bộ trên HttpExecutionContext của Play Framework.
 */
@Singleton
public class StockLedgerService {

    // Tập hợp các loại biến động kho hợp lệ theo nghiệp vụ nhập / xuất / chuyển / kiểm kê / hủy
    public static final Set<String> VALID_MOVEMENT_TYPES = Set.of(
            "NHAP", "BAN", "TRA_KH", "TRA_NCC", "HUY", "CHUYEN_XUAT", "CHUYEN_NHAN", "DIEU_CHINH"
    );

    private final StockLedgerRepository ledgerRepository;
    private final HttpExecutionContext httpExecutionContext;

    @Inject
    public StockLedgerService(StockLedgerRepository ledgerRepository, HttpExecutionContext httpExecutionContext) {
        this.ledgerRepository = ledgerRepository;
        this.httpExecutionContext = httpExecutionContext;
    }

    /**
     * Hàm dùng chung cho các nghiệp vụ khác ghi nhận biến động tồn kho trong cùng transaction.
     */
    public Long recordMovement(
            Connection conn,
            int branchId,
            int drugId,
            int lotId,
            String movementType,
            int quantityChange,
            int balanceAfter,
            BigDecimal unitCost,
            String referenceType,
            Long referenceId,
            String referenceCode,
            String note,
            int userId
    ) throws SQLException {
        if (!VALID_MOVEMENT_TYPES.contains(movementType)) {
            throw new IllegalArgumentException("Loại biến động kho không hợp lệ: " + movementType);
        }
        if (quantityChange == 0) {
            throw new IllegalArgumentException("Số lượng biến động kho phải khác 0");
        }
        if (balanceAfter < 0) {
            throw new IllegalArgumentException("Tồn sau biến động không được âm");
        }

        StockLedger ledger = new StockLedger();
        ledger.setBranchId(branchId);
        ledger.setDrugId(drugId);
        ledger.setLotId(lotId);
        ledger.setMovementType(movementType);
        ledger.setQuantityChange(quantityChange);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setUnitCost(unitCost);
        ledger.setReferenceType(referenceType);
        ledger.setReferenceId(referenceId);
        ledger.setReferenceCode(referenceCode);
        ledger.setNote(note);
        ledger.setCreatedBy(userId);
        ledger.setCreatedAt(LocalDateTime.now());

        return ledgerRepository.insert(conn, ledger);
    }

    /**
     * Kiểm tra tham số lọc và truy vấn thẻ kho bất đồng bộ.
     */
    public CompletionStage<PagedResponse> getStockLedger(
            int branchId,
            Integer drugId,
            Integer lotId,
            String type,
            String fromStr,
            String toStr,
            int page,
            int size,
            String sort
    ) {
        LocalDateTime fromDate = parseStartDate(fromStr);
        LocalDateTime toDate = parseEndDate(toStr);

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Tham số 'from' không được lớn hơn 'to'");
        }

        if (type != null && !type.isBlank() && !VALID_MOVEMENT_TYPES.contains(type.trim().toUpperCase())) {
            throw new IllegalArgumentException("Loại biến động 'type' không hợp lệ");
        }

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
                return ledgerRepository.findLedger(
                        branchId, drugId, lotId, type, fromDate, toDate, page, size, finalSortOrder
                );
            } catch (SQLException e) {
                throw new RuntimeException("Lỗi truy vấn cơ sở dữ liệu thẻ kho", e);
            }
        }, httpExecutionContext.current());
    }

    private LocalDateTime parseStartDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            if (dateStr.length() == 10) {
                return LocalDate.parse(dateStr).atStartOfDay();
            }
            return LocalDateTime.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Định dạng ngày 'from' không hợp lệ (dùng yyyy-MM-dd hoặc ISO-8601)");
        }
    }

    private LocalDateTime parseEndDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            if (dateStr.length() == 10) {
                return LocalDate.parse(dateStr).atTime(LocalTime.MAX);
            }
            return LocalDateTime.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Định dạng ngày 'to' không hợp lệ (dùng yyyy-MM-dd hoặc ISO-8601)");
        }
    }
}
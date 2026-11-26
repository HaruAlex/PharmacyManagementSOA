package repositories;

import models.PagedResponse;
import models.StockTransfer;
import models.StockTransferDto;
import models.StockTransferItem;
import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Tầng Repository xử lý giao dịch SQL Server cho phiếu chuyển kho (PhieuChuyenKho / ChiTietChuyenKho)
 * và cập nhật tồn kho theo lô tại chi nhánh nguồn & chi nhánh đích.
 */
@Singleton
public class StockTransferRepository {

    private final Database db;

    @Inject
    public StockTransferRepository(Database db) {
        this.db = db;
    }

    /**
     * Trả về Database để tầng Service mở transaction dùng chung với StockLedgerService.recordMovement.
     */
    public Database getDatabase() {
        return db;
    }

    /**
     * Lấy tồn kho hiện tại của một lô thuốc tại chi nhánh cụ thể.
     */
    public int getLotStock(Connection conn, int branchId, int drugId, int lotId) throws SQLException {
        String sql = "SELECT so_luong_ton FROM TonKhoLo " +
                "WHERE branch_id = ? AND drug_id = ? AND lot_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, branchId);
            ps.setInt(2, drugId);
            ps.setInt(3, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("so_luong_ton");
                }
            }
        }
        return 0;
    }

    /**
     * Cập nhật hoặc chèn bản ghi tồn kho cho lô thuốc tại chi nhánh (dùng cho cả chi nhánh xuất và nhận).
     */
    public int upsertLotStock(Connection conn, int branchId, int drugId, int lotId, int deltaQuantity) throws SQLException {
        // Thử cập nhật trước nếu dòng tồn kho của lô tại chi nhánh đã tồn tại
        String updateSql = "UPDATE TonKhoLo SET so_luong_ton = so_luong_ton + ? " +
                "WHERE branch_id = ? AND drug_id = ? AND lot_id = ? AND (so_luong_ton + ?) >= 0";
        try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
            updatePs.setInt(1, deltaQuantity);
            updatePs.setInt(2, branchId);
            updatePs.setInt(3, drugId);
            updatePs.setInt(4, lotId);
            updatePs.setInt(5, deltaQuantity);
            int updatedRows = updatePs.executeUpdate();
            if (updatedRows > 0) {
                return getLotStock(conn, branchId, drugId, lotId);
            }
        }

        // Nếu chưa có dòng tồn kho (trường hợp tăng tồn cho chi nhánh nhận mới) thì chèn mới
        if (deltaQuantity > 0) {
            String insertSql = "INSERT INTO TonKhoLo (branch_id, drug_id, lot_id, so_luong_ton) VALUES (?, ?, ?, ?)";
            try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                insertPs.setInt(1, branchId);
                insertPs.setInt(2, drugId);
                insertPs.setInt(3, lotId);
                insertPs.setInt(4, deltaQuantity);
                insertPs.executeUpdate();
                return deltaQuantity;
            }
        }

        throw new IllegalStateException("Không đủ tồn kho cho lô lotId=" + lotId + " tại chi nhánh branchId=" + branchId);
    }

    /**
     * Lưu phiếu chuyển kho và toàn bộ danh sách dòng thuốc thuộc phiếu trong cùng transaction.
     */
    public Long insertTransfer(Connection conn, StockTransfer transfer) throws SQLException {
        String headerSql = "INSERT INTO PhieuChuyenKho (" +
                "ma_phieu, chi_nhanh_xuat_id, chi_nhanh_nhan_id, trang_thai, ghi_chu, nguoi_tao, ngay_tao) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        Long transferId;
        try (PreparedStatement ps = conn.prepareStatement(headerSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, transfer.getTransferCode());
            ps.setInt(2, transfer.getSourceBranchId());
            ps.setInt(3, transfer.getTargetBranchId());
            ps.setString(4, transfer.getStatus());
            ps.setString(5, transfer.getNote());
            ps.setInt(6, transfer.getCreatedBy());
            ps.setTimestamp(7, Timestamp.valueOf(
                    transfer.getCreatedAt() != null ? transfer.getCreatedAt() : LocalDateTime.now()
            ));
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("Không thể lấy ID phiếu chuyển kho vừa tạo");
                }
                transferId = rs.getLong(1);
                transfer.setId(transferId);
            }
        }

        // Lưu từng dòng chi tiết chuyển kho
        String itemSql = "INSERT INTO ChiTietChuyenKho (phieu_chuyen_id, drug_id, lot_id, so_luong) " +
                "VALUES (?, ?, ?, ?)";
        try (PreparedStatement itemPs = conn.prepareStatement(itemSql)) {
            for (StockTransferItem item : transfer.getItems()) {
                item.setTransferId(transferId);
                itemPs.setLong(1, transferId);
                itemPs.setInt(2, item.getDrugId());
                itemPs.setInt(3, item.getLotId());
                itemPs.setInt(4, item.getQuantity());
                itemPs.addBatch();
            }
            itemPs.executeBatch();
        }

        return transferId;
    }

    /**
     * Truy vấn danh sách phiếu chuyển kho liên quan tới chi nhánh (xuất hoặc nhận), có phân trang chuẩn PagedResponse.
     */
    public PagedResponse findTransfers(
            int branchId,
            Integer targetBranchId,
            String status,
            int page,
            int size,
            String sortOrder
    ) throws SQLException {
        // Lọc các phiếu mà chi nhánh hiện hành tham gia (là chi nhánh xuất hoặc chi nhánh nhận)
        StringBuilder whereClause = new StringBuilder(
                " WHERE (p.chi_nhanh_xuat_id = ? OR p.chi_nhanh_nhan_id = ?) "
        );
        List<Object> params = new ArrayList<>();
        params.add(branchId);
        params.add(branchId);

        if (targetBranchId != null) {
            whereClause.append(" AND p.chi_nhanh_nhan_id = ? ");
            params.add(targetBranchId);
        }
        if (status != null && !status.isBlank()) {
            whereClause.append(" AND p.trang_thai = ? ");
            params.add(status.trim().toUpperCase());
        }

        String direction = "ASC".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        int offset = (page - 1) * size;

        String countSql = "SELECT COUNT(*) FROM PhieuChuyenKho p " + whereClause;
        String dataSql = "SELECT p.id, p.ma_phieu, p.chi_nhanh_xuat_id, p.chi_nhanh_nhan_id, " +
                "p.trang_thai, p.ghi_chu, p.nguoi_tao, p.ngay_tao " +
                "FROM PhieuChuyenKho p " + whereClause +
                " ORDER BY p.ngay_tao " + direction + ", p.id " + direction +
                " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        try (Connection conn = db.getConnection()) {
            long total = 0;
            try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                setParameters(countPs, params);
                try (ResultSet rs = countPs.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getLong(1);
                    }
                }
            }

            List<StockTransferDto> transfers = new ArrayList<>();
            List<Long> transferIds = new ArrayList<>();

            try (PreparedStatement dataPs = conn.prepareStatement(dataSql)) {
                setParameters(dataPs, params);
                int idx = params.size() + 1;
                dataPs.setInt(idx++, offset);
                dataPs.setInt(idx, size);

                try (ResultSet rs = dataPs.executeQuery()) {
                    while (rs.next()) {
                        StockTransferDto dto = mapRowToDto(rs);
                        transfers.add(dto);
                        transferIds.add(dto.id);
                    }
                }
            }

            // Nạp chi tiết lô thuốc cho các phiếu trên trang hiện tại
            if (!transferIds.isEmpty()) {
                Map<Long, List<StockTransferItem>> itemsByTransfer = findItemsByTransferIds(conn, transferIds);
                for (StockTransferDto dto : transfers) {
                    dto.items = itemsByTransfer.getOrDefault(dto.id, Collections.emptyList());
                }
            }

            List<Object> items = new ArrayList<>(transfers);
            return new PagedResponse(items, page, size, total);
        }
    }

    /**
     * Lấy chi tiết một phiếu chuyển kho theo ID (kèm danh sách dòng thuốc/lô).
     */
    public Optional<StockTransferDto> findById(long transferId) throws SQLException {
        String sql = "SELECT id, ma_phieu, chi_nhanh_xuat_id, chi_nhanh_nhan_id, " +
                "trang_thai, ghi_chu, nguoi_tao, ngay_tao " +
                "FROM PhieuChuyenKho WHERE id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, transferId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                StockTransferDto dto = mapRowToDto(rs);
                Map<Long, List<StockTransferItem>> itemsMap = findItemsByTransferIds(conn, List.of(transferId));
                dto.items = itemsMap.getOrDefault(transferId, Collections.emptyList());
                return Optional.of(dto);
            }
        }
    }

    /**
     * Nạp danh sách dòng chi tiết chuyển kho theo danh sách ID phiếu.
     */
    private Map<Long, List<StockTransferItem>> findItemsByTransferIds(Connection conn, List<Long> transferIds) throws SQLException {
        String placeholders = String.join(",", Collections.nCopies(transferIds.size(), "?"));
        String sql = "SELECT c.id, c.phieu_chuyen_id, c.drug_id, c.lot_id, l.so_lo, c.so_luong " +
                "FROM ChiTietChuyenKho c " +
                "LEFT JOIN LoThuoc l ON c.lot_id = l.id " +
                "WHERE c.phieu_chuyen_id IN (" + placeholders + ")";

        Map<Long, List<StockTransferItem>> result = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < transferIds.size(); i++) {
                ps.setLong(i + 1, transferIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StockTransferItem item = new StockTransferItem();
                    item.setId(rs.getLong("id"));
                    item.setTransferId(rs.getLong("phieu_chuyen_id"));
                    item.setDrugId(rs.getInt("drug_id"));
                    item.setLotId(rs.getInt("lot_id"));
                    item.setLotNumber(rs.getString("so_lo"));
                    item.setQuantity(rs.getInt("so_luong"));

                    result.computeIfAbsent(item.getTransferId(), k -> new ArrayList<>()).add(item);
                }
            }
        }
        return result;
    }

    private void setParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof Integer) {
                ps.setInt(i + 1, (Integer) p);
            } else if (p instanceof String) {
                ps.setString(i + 1, (String) p);
            }
        }
    }

    private StockTransferDto mapRowToDto(ResultSet rs) throws SQLException {
        StockTransferDto dto = new StockTransferDto();
        dto.id = rs.getLong("id");
        dto.transferCode = rs.getString("ma_phieu");
        dto.sourceBranchId = rs.getInt("chi_nhanh_xuat_id");
        dto.targetBranchId = rs.getInt("chi_nhanh_nhan_id");
        dto.status = rs.getString("trang_thai");
        dto.note = rs.getString("ghi_chu");
        dto.createdBy = rs.getInt("nguoi_tao");
        Timestamp created = rs.getTimestamp("ngay_tao");
        dto.createdAt = (created != null) ? created.toLocalDateTime() : null;
        return dto;
    }
}
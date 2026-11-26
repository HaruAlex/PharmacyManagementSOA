package repositories;

import models.PagedResponse;
import models.StockLedger;
import models.StockLedgerDto;
import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tầng Repository (DAO) truy xuất bảng BienDongKho trên SQL Server bằng JDBC + PreparedStatement.
 */
@Singleton
public class StockLedgerRepository {

    private final Database db;

    @Inject
    public StockLedgerRepository(Database db) {
        this.db = db;
    }

    /**
     * Thêm một bản ghi biến động kho sử dụng chung Connection/Transaction của nghiệp vụ gọi
     * (nhập kho, bán hàng FEFO, trả hàng, chuyển kho, kiểm kê, hủy thuốc) để đảm bảo tính nguyên tử.
     */
    public Long insert(Connection conn, StockLedger ledger) throws SQLException {
        String sql = "INSERT INTO BienDongKho (" +
                "branch_id, drug_id, lot_id, loai_bien_dong, so_luong_thay_doi, " +
                "ton_sau_bien_dong, don_gia_von, loai_chung_tu, chung_tu_id, ma_chung_tu, " +
                "ghi_chu, nguoi_thuc_hien, ngay_tao) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, ledger.getBranchId());
            ps.setInt(2, ledger.getDrugId());
            ps.setInt(3, ledger.getLotId());
            ps.setString(4, ledger.getMovementType());
            ps.setInt(5, ledger.getQuantityChange());
            ps.setInt(6, ledger.getBalanceAfter());

            // Cho phép null nếu biến động không gắn giá vốn riêng
            if (ledger.getUnitCost() != null) {
                ps.setBigDecimal(7, ledger.getUnitCost());
            } else {
                ps.setNull(7, Types.DECIMAL);
            }

            ps.setString(8, ledger.getReferenceType());

            // Cho phép null nếu chứng từ chưa có ID số
            if (ledger.getReferenceId() != null) {
                ps.setLong(9, ledger.getReferenceId());
            } else {
                ps.setNull(9, Types.BIGINT);
            }

            ps.setString(10, ledger.getReferenceCode());
            ps.setString(11, ledger.getNote());
            ps.setInt(12, ledger.getCreatedBy());
            ps.setTimestamp(13, Timestamp.valueOf(
                    ledger.getCreatedAt() != null ? ledger.getCreatedAt() : LocalDateTime.now()
            ));

            ps.executeUpdate();

            // Lấy ID bản ghi vừa sinh tự động từ SQL Server
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    /**
     * Truy vấn danh sách biến động thẻ kho theo chi nhánh và các bộ lọc tùy chọn (drugId, lotId, type, from, to),
     * có phân trang chuẩn { items, page, size, total } theo quy ước API của Proposal.
     */
    public PagedResponse findLedger(
            Integer branchId,
            Integer drugId,
            Integer lotId,
            String movementType,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            int page,
            int size,
            String sortOrder
    ) throws SQLException {

        // Luôn lọc theo chi nhánh hiện hành (X-Branch-Id) để đảm bảo cô lập dữ liệu theo chi nhánh
        StringBuilder whereClause = new StringBuilder(" WHERE b.branch_id = ? ");
        List<Object> params = new ArrayList<>();
        params.add(branchId);

        if (drugId != null) {
            whereClause.append(" AND b.drug_id = ? ");
            params.add(drugId);
        }
        if (lotId != null) {
            whereClause.append(" AND b.lot_id = ? ");
            params.add(lotId);
        }
        if (movementType != null && !movementType.isBlank()) {
            whereClause.append(" AND b.loai_bien_dong = ? ");
            params.add(movementType.trim().toUpperCase());
        }
        if (fromDate != null) {
            whereClause.append(" AND b.ngay_tao >= ? ");
            params.add(Timestamp.valueOf(fromDate));
        }
        if (toDate != null) {
            whereClause.append(" AND b.ngay_tao <= ? ");
            params.add(Timestamp.valueOf(toDate));
        }

        // Chỉ chấp nhận ASC hoặc DESC để tránh SQL injection ở mệnh đề ORDER BY
        String orderDirection = "ASC".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        int offset = (page - 1) * size;

        String countSql = "SELECT COUNT(*) FROM BienDongKho b " + whereClause;

        String dataSql = "SELECT b.id, b.branch_id, b.drug_id, b.lot_id, " +
                "l.so_lo, l.han_dung, " +
                "b.loai_bien_dong, b.so_luong_thay_doi, b.ton_sau_bien_dong, " +
                "b.don_gia_von, b.loai_chung_tu, b.chung_tu_id, b.ma_chung_tu, " +
                "b.ghi_chu, b.nguoi_thuc_hien, b.ngay_tao " +
                "FROM BienDongKho b " +
                "LEFT JOIN LoThuoc l ON b.lot_id = l.id " +
                whereClause +
                " ORDER BY b.ngay_tao " + orderDirection + ", b.id " + orderDirection +
                " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        try (Connection conn = db.getConnection()) {
            // 1. Đếm tổng số bản ghi khớp điều kiện
            long total = 0;
            try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                setParameters(countPs, params);
                try (ResultSet rs = countPs.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getLong(1);
                    }
                }
            }

            // 2. Lấy dữ liệu trang hiện tại
            List<Object> items = new ArrayList<>();
            try (PreparedStatement dataPs = conn.prepareStatement(dataSql)) {
                setParameters(dataPs, params);
                int idx = params.size() + 1;
                dataPs.setInt(idx++, offset);
                dataPs.setInt(idx, size);

                try (ResultSet rs = dataPs.executeQuery()) {
                    while (rs.next()) {
                        items.add(mapRowToDto(rs));
                    }
                }
            }

            return new PagedResponse(items, page, size, total);
        }
    }

    /**
     * Gán tham số động vào PreparedStatement đúng kiểu dữ liệu SQL.
     */
    private void setParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param instanceof Integer) {
                ps.setInt(i + 1, (Integer) param);
            } else if (param instanceof String) {
                ps.setString(i + 1, (String) param);
            } else if (param instanceof Timestamp) {
                ps.setTimestamp(i + 1, (Timestamp) param);
            }
        }
    }

    /**
     * Chuyển đổi một dòng ResultSet thành StockLedgerDto.
     */
    private StockLedgerDto mapRowToDto(ResultSet rs) throws SQLException {
        StockLedgerDto dto = new StockLedgerDto();
        dto.id = rs.getLong("id");
        dto.branchId = rs.getInt("branch_id");
        dto.drugId = rs.getInt("drug_id");
        dto.lotId = rs.getInt("lot_id");
        dto.lotNumber = rs.getString("so_lo");

        Date hanDung = rs.getDate("han_dung");
        dto.expiryDate = (hanDung != null) ? hanDung.toLocalDate() : null;

        dto.movementType = rs.getString("loai_bien_dong");
        dto.quantityChange = rs.getInt("so_luong_thay_doi");
        dto.balanceAfter = rs.getInt("ton_sau_bien_dong");
        dto.unitCost = rs.getBigDecimal("don_gia_von");
        dto.referenceType = rs.getString("loai_chung_tu");

        long refId = rs.getLong("chung_tu_id");
        dto.referenceId = rs.wasNull() ? null : refId;

        dto.referenceCode = rs.getString("ma_chung_tu");
        dto.note = rs.getString("ghi_chu");
        dto.createdBy = rs.getInt("nguoi_thuc_hien");

        Timestamp ngayTao = rs.getTimestamp("ngay_tao");
        dto.createdAt = (ngayTao != null) ? ngayTao.toLocalDateTime() : null;
        return dto;
    }
}
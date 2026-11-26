package repositories;

import models.MedicineStock;
import play.db.Database;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository truy vấn dữ liệu tồn kho từ SQL Server cho BE-28.
 * Sử dụng PreparedStatement để tránh SQL injection và try-with-resources để đóng kết nối an toàn.
 */
@Singleton
public class InventoryRepository {

    private final Database database;

    /**
     * Tiêm DataSource/Database của Play Framework vào Repository.
     *
     * @param database đối tượng quản lý kết nối cơ sở dữ liệu
     */
    @Inject
    public InventoryRepository(Database database) {
        this.database = database;
    }

    /**
     * Lấy danh sách tồn kho thuốc, hỗ trợ lọc theo từ khóa tên hoặc mã thuốc.
     *
     * @param keyword từ khóa tìm kiếm tùy chọn
     * @return danh sách {@link MedicineStock} sắp xếp theo tên thuốc tăng dần
     * @throws SQLException nếu lỗi kết nối hoặc thực thi câu lệnh SQL
     */
    public List<MedicineStock> findAll(String keyword) throws SQLException {
        String baseSql =
                "SELECT medicine_id, medicine_name, unit, total_quantity, min_stock_threshold, updated_at "
                        + "FROM inventory_stocks ";
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();

        // Chỉ gắn mệnh đề WHERE khi người dùng truyền từ khóa lọc
        String sql = hasKeyword
                ? baseSql + "WHERE LOWER(medicine_name) LIKE ? OR LOWER(medicine_id) LIKE ? ORDER BY medicine_name ASC"
                : baseSql + "ORDER BY medicine_name ASC";

        List<MedicineStock> result = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (hasKeyword) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                stmt.setString(1, pattern);
                stmt.setString(2, pattern);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    /**
     * Tìm thông tin tồn kho chi tiết theo mã thuốc.
     *
     * @param medicineId mã định danh thuốc
     * @return {@link Optional} chứa {@link MedicineStock} nếu tìm thấy, ngược lại rỗng
     * @throws SQLException nếu lỗi kết nối hoặc truy vấn SQL Server
     */
    public Optional<MedicineStock> findByMedicineId(String medicineId) throws SQLException {
        String sql =
                "SELECT medicine_id, medicine_name, unit, total_quantity, min_stock_threshold, updated_at "
                        + "FROM inventory_stocks WHERE medicine_id = ?";

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, medicineId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Ánh xạ một bản ghi {@link ResultSet} sang đối tượng {@link MedicineStock}.
     *
     * @param rs con trỏ dữ liệu kết quả truy vấn
     * @return đối tượng {@link MedicineStock} đã điền dữ liệu
     * @throws SQLException nếu đọc sai tên cột hoặc kiểu dữ liệu SQL
     */
    private MedicineStock mapRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("updated_at");
        return new MedicineStock(
                rs.getString("medicine_id"),
                rs.getString("medicine_name"),
                rs.getString("unit"),
                rs.getInt("total_quantity"),
                rs.getInt("min_stock_threshold"),
                ts != null ? ts.toInstant() : null
        );
    }
}
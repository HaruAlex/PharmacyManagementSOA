package repositories.jdbc;

import play.db.Database;
import repositories.UserBranchRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation của UserBranchRepository
 */
@Singleton
public class JdbcUserBranchRepository implements UserBranchRepository {
    private final Database database;

    @Inject
    public JdbcUserBranchRepository(Database database) {
        this.database = database;
    }

    @Override
    public List<Integer> getBranchIdsByUserId(Integer userId) {
        String sql = "SELECT BranchId FROM auth.UserBranches WHERE UserId = ? ORDER BY BranchId";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Integer> branchIds = new ArrayList<>();
                    while (rs.next()) {
                        branchIds.add(rs.getInt("BranchId"));
                    }
                    return branchIds;
                }
            }
        });
    }

    @Override
    public void replaceBranches(Integer userId, List<Integer> branchIds) {
        database.withTransaction(conn -> {
            try {
                // Xóa tất cả branches hiện tại
                deleteByUserIdInTransaction(conn, userId);
                
                // Thêm branches mới
                if (branchIds != null && !branchIds.isEmpty()) {
                    String insertSql = "INSERT INTO auth.UserBranches (UserId, BranchId) VALUES (?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        for (Integer branchId : branchIds) {
                            ps.setInt(1, userId);
                            ps.setInt(2, branchId);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Error replacing branches", e);
            }
        });
    }

    @Override
    public void deleteByUserId(Integer userId) {
        database.withConnection(conn -> {
            try {
                deleteByUserIdInTransaction(conn, userId);
            } catch (Exception e) {
                throw new RuntimeException("Error deleting user branches", e);
            }
        });
    }

    private void deleteByUserIdInTransaction(Connection conn, Integer userId) throws Exception {
        String sql = "DELETE FROM auth.UserBranches WHERE UserId = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }
}

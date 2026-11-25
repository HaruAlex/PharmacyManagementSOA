package repositories.jdbc;

import play.db.Database;
import repositories.RolePermissionRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

/**
 * JDBC implementation của RolePermissionRepository
 */
@Singleton
public class JdbcRolePermissionRepository implements RolePermissionRepository {
    private final Database database;

    @Inject
    public JdbcRolePermissionRepository(Database database) {
        this.database = database;
    }

    @Override
    public void replacePermissions(Integer roleId, List<Integer> permissionIds) {
        database.withTransaction(conn -> {
            try {
                // Xóa tất cả permissions hiện tại
                deleteByRoleIdInTransaction(conn, roleId);
                
                // Thêm permissions mới
                if (permissionIds != null && !permissionIds.isEmpty()) {
                    String insertSql = "INSERT INTO auth.RolePermissions (RoleId, PermissionId) VALUES (?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        for (Integer permissionId : permissionIds) {
                            ps.setInt(1, roleId);
                            ps.setInt(2, permissionId);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Error replacing permissions", e);
            }
        });
    }

    @Override
    public void deleteByRoleId(Integer roleId) {
        database.withConnection(conn -> {
            try {
                deleteByRoleIdInTransaction(conn, roleId);
            } catch (Exception e) {
                throw new RuntimeException("Error deleting role permissions", e);
            }
        });
    }

    private void deleteByRoleIdInTransaction(Connection conn, Integer roleId) throws Exception {
        String sql = "DELETE FROM auth.RolePermissions WHERE RoleId = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            ps.executeUpdate();
        }
    }
}

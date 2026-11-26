package repositories.jdbc;

import domain.Permission;
import play.db.Database;
import repositories.PermissionRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation của PermissionRepository
 */
@Singleton
public class JdbcPermissionRepository implements PermissionRepository {
    private final Database database;

    @Inject
    public JdbcPermissionRepository(Database database) {
        this.database = database;
    }

    @Override
    public Optional<Permission> getById(Integer permissionId) {
        String sql = "SELECT * FROM auth.Permissions WHERE PermissionId = ?";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, permissionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapPermission(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<Permission> getByCode(String permissionCode) {
        String sql = "SELECT * FROM auth.Permissions WHERE PermissionCode = ?";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, permissionCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapPermission(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Permission> getAll() {
        String sql = "SELECT * FROM auth.Permissions ORDER BY PermissionId";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                List<Permission> permissions = new ArrayList<>();
                while (rs.next()) {
                    permissions.add(mapPermission(rs));
                }
                return permissions;
            }
        });
    }

    @Override
    public List<Permission> getByRoleId(Integer roleId) {
        String sql = "SELECT p.* FROM auth.Permissions p " +
                     "INNER JOIN auth.RolePermissions rp ON p.PermissionId = rp.PermissionId " +
                     "WHERE rp.RoleId = ? ORDER BY p.PermissionId";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, roleId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Permission> permissions = new ArrayList<>();
                    while (rs.next()) {
                        permissions.add(mapPermission(rs));
                    }
                    return permissions;
                }
            }
        });
    }

    @Override
    public List<Permission> getByUserId(Integer userId) {
        String sql = "SELECT DISTINCT p.* FROM auth.Permissions p " +
                     "INNER JOIN auth.RolePermissions rp ON p.PermissionId = rp.PermissionId " +
                     "INNER JOIN auth.Users u ON rp.RoleId = u.RoleId " +
                     "WHERE u.UserId = ? ORDER BY p.PermissionId";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Permission> permissions = new ArrayList<>();
                    while (rs.next()) {
                        permissions.add(mapPermission(rs));
                    }
                    return permissions;
                }
            }
        });
    }

    @Override
    public boolean existsByCode(String permissionCode) {
        String sql = "SELECT COUNT(*) FROM auth.Permissions WHERE PermissionCode = ?";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, permissionCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                    return false;
                }
            }
        });
    }

    @Override
    public Integer create(Permission permission) {
        String sql = "INSERT INTO auth.Permissions (PermissionCode, PermissionName) VALUES (?, ?)";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, permission.getPermissionCode());
                ps.setString(2, permission.getPermissionName());
                ps.executeUpdate();
                
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    throw new SQLException("Failed to get generated PermissionId");
                }
            }
        });
    }

    @Override
    public void update(Permission permission) {
        String sql = "UPDATE auth.Permissions SET PermissionName = ? WHERE PermissionId = ?";
        database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, permission.getPermissionName());
                ps.setInt(2, permission.getPermissionId());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public boolean isAssignedToAnyRole(Integer permissionId) {
        String sql = "SELECT COUNT(*) FROM auth.RolePermissions WHERE PermissionId = ?";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, permissionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                    return false;
                }
            }
        });
    }

    @Override
    public void replaceRolePermissions(Integer roleId, List<Integer> permissionIds) {
        database.withTransaction(conn -> {
            // 1. Xóa tất cả permissions cũ của role
            String deleteSql = "DELETE FROM auth.RolePermissions WHERE RoleId = ?";
            try (PreparedStatement deletePs = conn.prepareStatement(deleteSql)) {
                deletePs.setInt(1, roleId);
                deletePs.executeUpdate();
            }
            
            // 2. Thêm permissions mới
            if (permissionIds != null && !permissionIds.isEmpty()) {
                String insertSql = "INSERT INTO auth.RolePermissions (RoleId, PermissionId) VALUES (?, ?)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    for (Integer permissionId : permissionIds) {
                        insertPs.setInt(1, roleId);
                        insertPs.setInt(2, permissionId);
                        insertPs.addBatch();
                    }
                    insertPs.executeBatch();
                }
            }
        });
    }

    private Permission mapPermission(ResultSet rs) throws SQLException {
        Permission permission = new Permission();
        permission.setPermissionId(rs.getInt("PermissionId"));
        permission.setPermissionCode(rs.getString("PermissionCode"));
        permission.setPermissionName(rs.getString("PermissionName"));
        return permission;
    }
}

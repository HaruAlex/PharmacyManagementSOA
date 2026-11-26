package repositories.jdbc;

import domain.Role;
import play.db.Database;
import repositories.RoleRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation của RoleRepository
 */
@Singleton
public class JdbcRoleRepository implements RoleRepository {
    private final Database database;

    @Inject
    public JdbcRoleRepository(Database database) {
        this.database = database;
    }

    @Override
    public Optional<Role> getById(Integer roleId) {
        String sql = "SELECT * FROM auth.Roles WHERE RoleId = ?";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, roleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRole(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<Role> getByCode(String roleCode) {
        String sql = "SELECT * FROM auth.Roles WHERE RoleCode = ?";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, roleCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRole(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Role> getAll() {
        String sql = "SELECT * FROM auth.Roles ORDER BY RoleId";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                List<Role> roles = new ArrayList<>();
                while (rs.next()) {
                    roles.add(mapRole(rs));
                }
                return roles;
            }
        });
    }

    @Override
    public List<Role> getAllActive() {
        String sql = "SELECT * FROM auth.Roles WHERE IsActive = 1 ORDER BY RoleId";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                List<Role> roles = new ArrayList<>();
                while (rs.next()) {
                    roles.add(mapRole(rs));
                }
                return roles;
            }
        });
    }

    @Override
    public boolean existsByCode(String roleCode) {
        String sql = "SELECT COUNT(*) FROM auth.Roles WHERE RoleCode = ?";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, roleCode);
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
    public Integer create(Role role) {
        String sql = "INSERT INTO auth.Roles (RoleCode, RoleName, IsActive) VALUES (?, ?, ?)";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, role.getRoleCode());
                ps.setString(2, role.getRoleName());
                ps.setBoolean(3, role.isActive());
                ps.executeUpdate();
                
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    throw new SQLException("Failed to get generated RoleId");
                }
            }
        });
    }

    @Override
    public void update(Role role) {
        String sql = "UPDATE auth.Roles SET RoleName = ?, IsActive = ? WHERE RoleId = ?";
        database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, role.getRoleName());
                ps.setBoolean(2, role.isActive());
                ps.setInt(3, role.getRoleId());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void setActive(Integer roleId, boolean active) {
        String sql = "UPDATE auth.Roles SET IsActive = ? WHERE RoleId = ?";
        database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, active);
                ps.setInt(2, roleId);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public boolean isInUseByActiveUsers(Integer roleId) {
        String sql = "SELECT COUNT(*) FROM auth.Users WHERE RoleId = ? AND IsActive = 1";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, roleId);
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
    public long countActiveUsersByRole(Integer roleId) {
        String sql = "SELECT COUNT(*) FROM auth.Users WHERE RoleId = ? AND IsActive = 1";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, roleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    return 0L;
                }
            }
        });
    }

    @Override
    public List<Integer> getUserIdsByRoleId(Integer roleId) {
        String sql = "SELECT UserId FROM auth.Users WHERE RoleId = ?";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, roleId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Integer> userIds = new ArrayList<>();
                    while (rs.next()) {
                        userIds.add(rs.getInt("UserId"));
                    }
                    return userIds;
                }
            }
        });
    }

    private Role mapRole(ResultSet rs) throws SQLException {
        Role role = new Role();
        role.setRoleId(rs.getInt("RoleId"));
        role.setRoleCode(rs.getString("RoleCode"));
        role.setRoleName(rs.getString("RoleName"));
        role.setActive(rs.getBoolean("IsActive"));
        return role;
    }
}

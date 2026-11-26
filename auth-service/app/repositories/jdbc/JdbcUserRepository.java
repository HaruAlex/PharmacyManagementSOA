package repositories.jdbc;

import domain.User;
import play.db.Database;
import repositories.UserRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation của UserRepository
 */
@Singleton
public class JdbcUserRepository implements UserRepository {
    private final Database database;
    
    // Whitelist các cột được phép sort
    private static final List<String> SORT_WHITELIST = Arrays.asList(
        "UserId", "Username", "FullName", "Email", "CreatedAt", "UpdatedAt"
    );

    @Inject
    public JdbcUserRepository(Database database) {
        this.database = database;
    }

    @Override
    public Optional<User> getById(Integer userId) {
        String sql = "SELECT * FROM auth.Users WHERE UserId = ?";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapUser(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<User> getByUsername(String username) {
        String sql = "SELECT * FROM auth.Users WHERE Username = ? COLLATE Latin1_General_CI_AS";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapUser(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<User> getByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM auth.Users WHERE Email = ? COLLATE Latin1_General_CI_AS";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapUser(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<User> getByUsernameOrEmail(String usernameOrEmail) {
        String sql = "SELECT * FROM auth.Users WHERE Username = ? COLLATE Latin1_General_CI_AS OR Email = ? COLLATE Latin1_General_CI_AS";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                String trimmed = usernameOrEmail.trim();
                ps.setString(1, trimmed);
                ps.setString(2, trimmed);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapUser(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public boolean existsUsername(String username) {
        String sql = "SELECT COUNT(*) FROM auth.Users WHERE Username = ? COLLATE Latin1_General_CI_AS AND IsActive = 1";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username.trim());
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
    public boolean existsEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM auth.Users WHERE Email = ? COLLATE Latin1_General_CI_AS AND IsActive = 1";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email.trim());
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
    public List<User> search(String nameFilter, String emailFilter, Integer roleId,
                            Boolean isActive, Boolean isLocked,
                            int page, int pageSize, String sortBy, String sortOrder) {
        StringBuilder sql = new StringBuilder("SELECT * FROM auth.Users WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (nameFilter != null && !nameFilter.trim().isEmpty()) {
            sql.append(" AND (FullName LIKE ? OR Username LIKE ?)");
            String pattern = "%" + nameFilter.trim() + "%";
            params.add(pattern);
            params.add(pattern);
        }

        if (emailFilter != null && !emailFilter.trim().isEmpty()) {
            sql.append(" AND Email LIKE ?");
            params.add("%" + emailFilter.trim() + "%");
        }

        if (roleId != null) {
            sql.append(" AND RoleId = ?");
            params.add(roleId);
        }

        if (isActive != null) {
            sql.append(" AND IsActive = ?");
            params.add(isActive ? 1 : 0);
        }
        
        if (isLocked != null) {
            sql.append(" AND IsLocked = ?");
            params.add(isLocked ? 1 : 0);
        }

        // Kiểm tra và thêm ORDER BY
        String validSortBy = SORT_WHITELIST.contains(sortBy) ? sortBy : "UserId";
        String validSortOrder = "DESC".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        sql.append(" ORDER BY ").append(validSortBy).append(" ").append(validSortOrder);

        // Phân trang
        int offset = (page - 1) * pageSize;
        sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(pageSize);

        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    List<User> users = new ArrayList<>();
                    while (rs.next()) {
                        users.add(mapUser(rs));
                    }
                    return users;
                }
            }
        });
    }

    @Override
    public long count(String nameFilter, String emailFilter, Integer roleId, 
                      Boolean isActive, Boolean isLocked) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM auth.Users WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (nameFilter != null && !nameFilter.trim().isEmpty()) {
            sql.append(" AND (FullName LIKE ? OR Username LIKE ?)");
            String pattern = "%" + nameFilter.trim() + "%";
            params.add(pattern);
            params.add(pattern);
        }

        if (emailFilter != null && !emailFilter.trim().isEmpty()) {
            sql.append(" AND Email LIKE ?");
            params.add("%" + emailFilter.trim() + "%");
        }

        if (roleId != null) {
            sql.append(" AND RoleId = ?");
            params.add(roleId);
        }

        if (isActive != null) {
            sql.append(" AND IsActive = ?");
            params.add(isActive ? 1 : 0);
        }
        
        if (isLocked != null) {
            sql.append(" AND IsLocked = ?");
            params.add(isLocked ? 1 : 0);
        }

        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
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
    public Integer create(User user) {
        String sql = "INSERT INTO auth.Users (RoleId, Username, PasswordHash, FullName, Phone, Email, IsLocked, FailedLoginCount, IsActive) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, user.getRoleId());
                ps.setString(2, user.getUsername().trim());
                ps.setString(3, user.getPasswordHash());
                ps.setString(4, user.getFullName());
                ps.setString(5, user.getPhone());
                ps.setString(6, user.getEmail());
                ps.setBoolean(7, user.isLocked());
                ps.setInt(8, user.getFailedLoginCount());
                ps.setBoolean(9, user.isActive());
                
                ps.executeUpdate();
                
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    throw new SQLException("Failed to get generated UserId");
                }
            }
        });
    }

    @Override
    public void update(User user) {
        String sql = "UPDATE auth.Users SET RoleId = ?, FullName = ?, Phone = ?, Email = ?, UpdatedAt = SYSDATETIME() WHERE UserId = ?";
        database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, user.getRoleId());
                ps.setString(2, user.getFullName());
                ps.setString(3, user.getPhone());
                ps.setString(4, user.getEmail());
                ps.setInt(5, user.getUserId());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void setLocked(Integer userId, boolean locked) {
        String sql = "UPDATE auth.Users SET IsLocked = ?, UpdatedAt = SYSDATETIME() WHERE UserId = ?";
        database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, locked);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void updatePasswordHash(Integer userId, String passwordHash) {
        String sql = "UPDATE auth.Users SET PasswordHash = ?, UpdatedAt = SYSDATETIME() WHERE UserId = ?";
        database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, passwordHash);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void incrementFailedLogin(Integer userId) {
        String sql = "UPDATE auth.Users SET FailedLoginCount = FailedLoginCount + 1, UpdatedAt = SYSDATETIME() WHERE UserId = ?";
        database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void resetFailedLogin(Integer userId) {
        String sql = "UPDATE auth.Users SET FailedLoginCount = 0, UpdatedAt = SYSDATETIME() WHERE UserId = ?";
        database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void setActive(Integer userId, boolean active) {
        String sql = "UPDATE auth.Users SET IsActive = ?, UpdatedAt = SYSDATETIME() WHERE UserId = ?";
        database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, active);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public long countActiveAdmins() {
        String sql = "SELECT COUNT(*) FROM auth.Users u " +
                     "INNER JOIN auth.Roles r ON u.RoleId = r.RoleId " +
                     "WHERE r.RoleCode = 'AD' AND u.IsActive = 1 AND u.IsLocked = 0";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
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

    private User mapUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("UserId"));
        user.setRoleId(rs.getInt("RoleId"));
        user.setUsername(rs.getString("Username"));
        user.setPasswordHash(rs.getString("PasswordHash"));
        user.setFullName(rs.getString("FullName"));
        user.setPhone(rs.getString("Phone"));
        user.setEmail(rs.getString("Email"));
        user.setLocked(rs.getBoolean("IsLocked"));
        user.setFailedLoginCount(rs.getInt("FailedLoginCount"));
        user.setActive(rs.getBoolean("IsActive"));
        
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toInstant());
        }
        
        Timestamp updatedAt = rs.getTimestamp("UpdatedAt");
        if (updatedAt != null) {
            user.setUpdatedAt(updatedAt.toInstant());
        }
        
        return user;
    }
}

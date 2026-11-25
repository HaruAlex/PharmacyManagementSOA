package repositories.jdbc;

import domain.RefreshToken;
import play.db.Database;
import repositories.RefreshTokenRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.*;
import java.util.Optional;

/**
 * JDBC implementation của RefreshTokenRepository
 */
@Singleton
public class JdbcRefreshTokenRepository implements RefreshTokenRepository {
    private final Database database;

    @Inject
    public JdbcRefreshTokenRepository(Database database) {
        this.database = database;
    }

    @Override
    public Optional<RefreshToken> getByTokenHash(String tokenHash) {
        String sql = "SELECT * FROM auth.RefreshTokens WHERE TokenHash = ?";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tokenHash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRefreshToken(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Long create(RefreshToken token) {
        String sql = "INSERT INTO auth.RefreshTokens (UserId, TokenHash, ExpiresAt, RevokedAt) VALUES (?, ?, ?, ?)";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, token.getUserId());
                ps.setString(2, token.getTokenHash());
                ps.setTimestamp(3, Timestamp.from(token.getExpiresAt()));
                ps.setTimestamp(4, token.getRevokedAt() != null ? Timestamp.from(token.getRevokedAt()) : null);
                ps.executeUpdate();
                
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    throw new SQLException("Failed to get generated RefreshTokenId");
                }
            }
        });
    }

    @Override
    public void revoke(Long refreshTokenId) {
        String sql = "UPDATE auth.RefreshTokens SET RevokedAt = SYSDATETIME() WHERE RefreshTokenId = ?";
        database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, refreshTokenId);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public int revokeAllByUserId(Integer userId) {
        String sql = "UPDATE auth.RefreshTokens SET RevokedAt = SYSDATETIME() WHERE UserId = ? AND RevokedAt IS NULL";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public int deleteExpired() {
        String sql = "DELETE FROM auth.RefreshTokens WHERE ExpiresAt < SYSDATETIME()";
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                return ps.executeUpdate();
            }
        });
    }

    private RefreshToken mapRefreshToken(ResultSet rs) throws SQLException {
        RefreshToken token = new RefreshToken();
        token.setRefreshTokenId(rs.getLong("RefreshTokenId"));
        token.setUserId(rs.getInt("UserId"));
        token.setTokenHash(rs.getString("TokenHash"));
        
        Timestamp expiresAt = rs.getTimestamp("ExpiresAt");
        if (expiresAt != null) {
            token.setExpiresAt(expiresAt.toInstant());
        }
        
        Timestamp revokedAt = rs.getTimestamp("RevokedAt");
        if (revokedAt != null) {
            token.setRevokedAt(revokedAt.toInstant());
        }
        
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) {
            token.setCreatedAt(createdAt.toInstant());
        }
        
        return token;
    }
}

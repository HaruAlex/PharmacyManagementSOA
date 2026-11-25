package repositories;

import domain.RefreshToken;
import java.util.Optional;

/**
 * Repository interface cho RefreshToken entity
 */
public interface RefreshTokenRepository {
    Optional<RefreshToken> getByTokenHash(String tokenHash);
    
    Long create(RefreshToken token);
    void revoke(Long refreshTokenId);
    
    /**
     * Revoke tất cả refresh token của user
     * @return Số lượng tokens đã revoke
     */
    int revokeAllByUserId(Integer userId);
    
    /**
     * Xóa tất cả token đã hết hạn
     */
    int deleteExpired();
}

package services;

import com.typesafe.config.Config;
import domain.AuditActions;
import domain.ErrorCodes;
import domain.RefreshToken;
import domain.User;
import errors.ApiException;
import repositories.RefreshTokenRepository;
import repositories.UserRepository;
import security.RefreshTokenGenerator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Dịch vụ xử lý nghiệp vụ refresh token
 */
@Singleton
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final RefreshTokenGenerator tokenGenerator;
    private final Clock clock;
    private final int refreshTokenDays;

    @Inject
    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            AuditService auditService,
            RefreshTokenGenerator tokenGenerator,
            Clock clock,
            Config config) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
        this.refreshTokenDays = config.getInt("jwt.refreshTokenDays");
    }

    /**
     * Tạo refresh token mới cho user
     * @param userId User ID
     * @param ipAddress IP address (để ghi audit)
     * @return Refresh token (plain text, chỉ trả về 1 lần)
     */
    public String createRefreshToken(Integer userId, String ipAddress) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(refreshTokenDays, ChronoUnit.DAYS);
        
        // Sinh token ngẫu nhiên
        String plainToken = tokenGenerator.generate();
        String tokenHash = tokenGenerator.hash(plainToken);
        
        // Lưu vào DB (chỉ lưu hash)
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(expiresAt);
        refreshToken.setRevokedAt(null);
        refreshToken.setCreatedAt(now);
        
        refreshTokenRepository.create(refreshToken);
        
        return plainToken;
    }

    /**
     * Xoay vòng refresh token: thu hồi token cũ, tạo token mới và giữ nguyên ExpiresAt
     * @param plainToken Token cũ (plain text)
     * @param ipAddress IP address
     * @return Token mới (plain text)
     * @throws ApiException nếu token không hợp lệ
     */
    public String rotateRefreshToken(Integer userId, String plainToken, String ipAddress) {
        String tokenHash = tokenGenerator.hash(plainToken);
        
        // Tìm token trong DB
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.getByTokenHash(tokenHash);
        if (tokenOpt.isEmpty()) {
            throw ApiException.unauthorized(ErrorCodes.AUTH_REFRESH_INVALID, "Invalid refresh token");
        }
        
        RefreshToken oldToken = tokenOpt.get();
        
        // Kiểm tra token đã bị revoke chưa
        if (oldToken.isRevoked()) {
            // REUSE DETECTION: Token đã revoke mà còn dùng → nghi ngờ bị đánh cắp
            // Thu hồi tất cả token của người dùng
            refreshTokenRepository.revokeAllByUserId(userId);
            
            auditService.write(userId, null, AuditActions.REFRESH_TOKEN_REUSE, "RefreshToken", null,
                String.format("Refresh token reuse detected, all tokens revoked. IP: %s", ipAddress));
            
            throw ApiException.unauthorized(ErrorCodes.AUTH_REFRESH_REVOKED, 
                "Refresh token has been revoked. All sessions have been invalidated for security.");
        }
        
        // Kiểm tra token đã hết hạn chưa
        Instant now = clock.instant();
        if (oldToken.isExpired(now)) {
            throw ApiException.unauthorized(ErrorCodes.AUTH_REFRESH_EXPIRED, 
                "Refresh token has expired. Please login again.");
        }
        
        // Kiểm tra user còn active và không bị lock
        Optional<User> userOpt = userRepository.getById(userId);
        if (userOpt.isEmpty()) {
            throw ApiException.unauthorized(ErrorCodes.AUTH_TOKEN_REVOKED, "User not found");
        }
        
        User user = userOpt.get();
        if (!user.isActive()) {
            throw ApiException.unauthorized(ErrorCodes.AUTH_ACCOUNT_INACTIVE, "Account is inactive");
        }
        if (user.isLocked()) {
            throw ApiException.unauthorized(ErrorCodes.AUTH_ACCOUNT_LOCKED, "Account is locked");
        }
        
        // Thu hồi token cũ
        refreshTokenRepository.revoke(oldToken.getRefreshTokenId());
        
        // Tạo token mới với CÙNG ExpiresAt (không gia hạn)
        String newPlainToken = tokenGenerator.generate();
        String newTokenHash = tokenGenerator.hash(newPlainToken);
        
        RefreshToken newToken = new RefreshToken();
        newToken.setUserId(userId);
        newToken.setTokenHash(newTokenHash);
        newToken.setExpiresAt(oldToken.getExpiresAt()); // ⚠️ Giữ nguyên ExpiresAt
        newToken.setRevokedAt(null);
        newToken.setCreatedAt(now);
        
        refreshTokenRepository.create(newToken);
        
        return newPlainToken;
    }

    /**
     * Revoke một refresh token (logout)
     */
    public void revokeToken(String plainToken, Integer userId, String ipAddress) {
        String tokenHash = tokenGenerator.hash(plainToken);
        
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.getByTokenHash(tokenHash);
        if (tokenOpt.isPresent()) {
            RefreshToken token = tokenOpt.get();
            if (!token.isRevoked()) {
                refreshTokenRepository.revoke(token.getRefreshTokenId());
                
                auditService.write(userId, null, AuditActions.LOGOUT_SUCCESS, "RefreshToken", null,
                    String.format("User logged out. IP: %s", ipAddress));
            }
        }
        // Không ném ngoại lệ nếu token không tồn tại để thao tác có thể gọi lặp an toàn
    }

    /**
     * Lấy refresh token theo hash (để lookup userId)
     */
    public Optional<RefreshToken> getByTokenHash(String tokenHash) {
        return refreshTokenRepository.getByTokenHash(tokenHash);
    }

    /**
     * Thu hồi tất cả refresh token của người dùng (đăng xuất mọi phiên)
     * @return Số lượng tokens đã revoke
     */
    public int revokeAllTokens(Integer userId, String ipAddress) {
        int revokedCount = refreshTokenRepository.revokeAllByUserId(userId);
        
        auditService.write(userId, null, AuditActions.LOGOUT_ALL_SUCCESS, "RefreshToken", null,
            String.format("User logged out from all devices, %d tokens revoked. IP: %s", revokedCount, ipAddress));
        
        return revokedCount;
    }

    /**
     * Xóa các refresh token đã hết hạn
     */
    public int cleanupExpiredTokens() {
        return refreshTokenRepository.deleteExpired();
    }

}

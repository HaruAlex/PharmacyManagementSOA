package security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.inject.Singleton;

/**
 * Bộ tạo refresh token
 * - Sinh chuỗi ngẫu nhiên 64 bytes
 * - Hash bằng SHA-256 để lưu vào DB
 */
@Singleton
public class RefreshTokenGenerator {
    private static final int TOKEN_LENGTH_BYTES = 64;
    private final SecureRandom secureRandom;

    public RefreshTokenGenerator() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Sinh refresh token ngẫu nhiên (Base64Url encoded)
     */
    public String generate() {
        byte[] randomBytes = new byte[TOKEN_LENGTH_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Hash token bằng SHA-256 để lưu vào DB
     */
    public String hash(String token) {
        return hashToken(token);
    }

    /**
     * Static method để hash token (dùng khi không inject RefreshTokenGenerator)
     */
    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash refresh token", e);
        }
    }
}

package security;

/**
 * Giao diện băm mật khẩu
 */
public interface PasswordHasher {
    /**
     * Hash password bằng BCrypt
     */
    String hash(String password);
    
    /**
     * Xác thực mật khẩu dựa trên hash
     */
    boolean verify(String password, String hash);
}

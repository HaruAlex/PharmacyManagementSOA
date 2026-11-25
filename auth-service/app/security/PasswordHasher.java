package security;

/**
 * Interface cho password hashing
 */
public interface PasswordHasher {
    /**
     * Hash password bằng BCrypt
     */
    String hash(String password);
    
    /**
     * Verify password với hash
     */
    boolean verify(String password, String hash);
}

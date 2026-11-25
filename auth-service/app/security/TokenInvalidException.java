package security;

/**
 * Exception khi token không hợp lệ
 */
public class TokenInvalidException extends RuntimeException {
    public TokenInvalidException(String message) {
        super(message);
    }
    
    public TokenInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}

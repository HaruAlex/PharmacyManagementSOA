package errors;

import play.mvc.Http;

/**
 * Exception cho API errors với status code và error code
 */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    
    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    
    public ApiException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }
    
    public int getStatus() {
        return status;
    }
    
    public String getCode() {
        return code;
    }
    
    // Helper methods cho các lỗi thường gặp
    
    public static ApiException unauthorized(String code, String message) {
        return new ApiException(Http.Status.UNAUTHORIZED, code, message);
    }
    
    public static ApiException forbidden(String code, String message) {
        return new ApiException(Http.Status.FORBIDDEN, code, message);
    }
    
    public static ApiException notFound(String code, String message) {
        return new ApiException(Http.Status.NOT_FOUND, code, message);
    }
    
    public static ApiException badRequest(String code, String message) {
        return new ApiException(Http.Status.BAD_REQUEST, code, message);
    }
    
    public static ApiException conflict(String code, String message) {
        return new ApiException(Http.Status.CONFLICT, code, message);
    }
    
    public static ApiException locked(String code, String message) {
        return new ApiException(423, code, message); // 423 Locked
    }
}

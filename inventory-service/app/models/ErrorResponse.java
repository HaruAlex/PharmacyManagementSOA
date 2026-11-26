// Đường dẫn file: models/ErrorResponse.java
package models;

import java.util.Map;

/**
 * Cấu trúc phản hồi chuẩn khi xảy ra lỗi trong quá trình xử lý yêu cầu.
 */
public class ErrorResponse {
    // Mã lỗi nghiệp vụ hoặc mã định danh lỗi.
    private String code;
    // Thông điệp mô tả lỗi dễ đọc cho phía gọi API hoặc người dùng.
    private String message;
    // Thông tin chi tiết bổ sung theo cặp khóa - giá trị.
    private Map<String, Object> details;

    /**
     * Khởi tạo đối tượng phản hồi lỗi.
     */
    public ErrorResponse(String code, String message, Map<String, Object> details) {
        this.code = code;
        this.message = message;
        this.details = details;
    }

    // Các phương thức getter truy xuất thông tin lỗi.
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Map<String, Object> getDetails() { return details; }
}
package dto;

import javax.validation.constraints.*;

/**
 * Đối tượng dữ liệu yêu cầu Admin đặt lại mật khẩu người dùng
 */
public class ResetPasswordRequest {
    @NotBlank(message = "New password is required")
    private String newPassword;

    // Getters and Setters
    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}

package dto;

import javax.validation.constraints.*;

/**
 * Request DTO để admin reset mật khẩu user
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

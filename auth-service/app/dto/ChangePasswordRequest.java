package dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Đối tượng dữ liệu yêu cầu đổi mật khẩu
 */
public class ChangePasswordRequest {
    @JsonProperty("currentPassword")
    private String currentPassword;
    
    @JsonProperty("newPassword")
    private String newPassword;
    
    @JsonProperty("confirmNewPassword")
    private String confirmNewPassword;

    // Getters and Setters
    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmNewPassword() {
        return confirmNewPassword;
    }

    public void setConfirmNewPassword(String confirmNewPassword) {
        this.confirmNewPassword = confirmNewPassword;
    }
}

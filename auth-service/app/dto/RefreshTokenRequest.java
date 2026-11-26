package dto;

import javax.validation.constraints.NotBlank;

/**
 * Đối tượng dữ liệu yêu cầu làm mới token
 */
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
    
    public RefreshTokenRequest() {}
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}

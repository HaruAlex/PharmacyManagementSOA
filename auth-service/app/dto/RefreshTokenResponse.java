package dto;

/**
 * Đối tượng dữ liệu phản hồi làm mới token
 */
public class RefreshTokenResponse {
    private String accessToken;
    private int expiresIn; // seconds
    private String refreshToken;
    
    public RefreshTokenResponse() {}
    
    public RefreshTokenResponse(String accessToken, int expiresIn, String refreshToken) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.refreshToken = refreshToken;
    }
    
    public String getAccessToken() {
        return accessToken;
    }
    
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    
    public int getExpiresIn() {
        return expiresIn;
    }
    
    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}

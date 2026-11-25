package security;

import java.util.List;

/**
 * Interface cho JWT token service
 */
public interface TokenService {
    /**
     * Tạo access token (JWT)
     * @param userId User ID
     * @param roleCode Role code
     * @param branchIds Danh sách branch IDs
     * @param currentBranchId Branch ID hiện tại (nullable)
     * @return JWT token string
     */
    String createAccessToken(Integer userId, String roleCode, List<Integer> branchIds, Integer currentBranchId);
    
    /**
     * Verify và parse JWT token
     * @param token JWT token string
     * @return TokenPayload nếu hợp lệ
     * @throws TokenInvalidException nếu token không hợp lệ hoặc hết hạn
     */
    TokenPayload verifyToken(String token);
    
    /**
     * Payload của JWT token
     */
    class TokenPayload {
        private Integer userId;
        private String roleCode;
        private List<Integer> branchIds;
        private Integer currentBranchId;
        private String jti;
        
        public TokenPayload() {}
        
        public TokenPayload(Integer userId, String roleCode, List<Integer> branchIds, Integer currentBranchId, String jti) {
            this.userId = userId;
            this.roleCode = roleCode;
            this.branchIds = branchIds;
            this.currentBranchId = currentBranchId;
            this.jti = jti;
        }
        
        public Integer getUserId() { return userId; }
        public void setUserId(Integer userId) { this.userId = userId; }
        
        public String getRoleCode() { return roleCode; }
        public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
        
        public List<Integer> getBranchIds() { return branchIds; }
        public void setBranchIds(List<Integer> branchIds) { this.branchIds = branchIds; }
        
        public Integer getCurrentBranchId() { return currentBranchId; }
        public void setCurrentBranchId(Integer currentBranchId) { this.currentBranchId = currentBranchId; }
        
        public String getJti() { return jti; }
        public void setJti(String jti) { this.jti = jti; }
    }
}

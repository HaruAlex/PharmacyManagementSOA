package services;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.typesafe.config.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;

@Singleton
public final class GatewayJwtVerifier {
    private final JWTVerifier verifier;

    @Inject
    public GatewayJwtVerifier(Config config) {
        String secret = config.getString("jwt.secret");
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.verifier = JWT.require(Algorithm.HMAC256(secret))
                .acceptLeeway(config.getLong("jwt.clock-skew-seconds"))
                .build();
    }

    public Claims verify(String token) {
        DecodedJWT jwt = verifier.verify(token);
        if (jwt.getExpiresAt() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new IllegalArgumentException("JWT is missing required claims");
        }
        int userId = Integer.parseInt(jwt.getSubject());
        String role = jwt.getClaim("role").asString();
        List<Integer> branchIds = jwt.getClaim("branch_ids").asList(Integer.class);
        if (userId < 1 || role == null || role.isBlank() || branchIds == null
                || branchIds.stream().anyMatch(branchId -> branchId == null || branchId < 1)) {
            throw new IllegalArgumentException("JWT contains invalid identity claims");
        }
        Integer currentBranchId = jwt.getClaim("current_branch_id").isNull()
                ? null
                : jwt.getClaim("current_branch_id").asInt();
        if (currentBranchId != null && !branchIds.contains(currentBranchId)) {
            throw new IllegalArgumentException("JWT current branch is not assigned to the user");
        }
        return new Claims(userId, role, List.copyOf(branchIds), currentBranchId);
    }

    public static final class Claims {
        private final int userId;
        private final String role;
        private final List<Integer> branchIds;
        private final Integer currentBranchId;

        private Claims(int userId, String role, List<Integer> branchIds, Integer currentBranchId) {
            this.userId = userId;
            this.role = role;
            this.branchIds = branchIds;
            this.currentBranchId = currentBranchId;
        }

        public int getUserId() {
            return userId;
        }

        public String getRole() {
            return role;
        }

        public List<Integer> getBranchIds() {
            return Collections.unmodifiableList(branchIds);
        }

        public Integer getCurrentBranchId() {
            return currentBranchId;
        }
    }
}

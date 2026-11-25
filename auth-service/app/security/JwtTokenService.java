package security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import play.Environment;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT implementation của TokenService
 */
@Singleton
public class JwtTokenService implements TokenService {
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final int accessTokenMinutes;
    private final ObjectMapper objectMapper;

    @Inject
    public JwtTokenService(Config config, Environment environment) {
        String secret = config.getString("jwt.secret");
        
        // Production: bắt buộc phải có JWT_SECRET và >= 32 bytes
        if (environment.isProd()) {
            if (secret == null || secret.length() < 32) {
                throw new RuntimeException(
                    "JWT_SECRET is required in production and must be at least 32 bytes. " +
                    "Set environment variable JWT_SECRET."
                );
            }
        } else {
            // Dev: cảnh báo nếu dùng secret mặc định
            if ("default-secret-key-for-development-min-32-bytes".equals(secret)) {
                System.err.println("[WARNING] Using default JWT secret. DO NOT use in production!");
            }
        }
        
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).build();
        this.accessTokenMinutes = config.getInt("jwt.accessTokenMinutes");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String createAccessToken(Integer userId, String roleCode, List<Integer> branchIds, Integer currentBranchId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenMinutes, ChronoUnit.MINUTES);
        
        var builder = JWT.create()
            .withSubject(userId.toString())
            .withClaim("role", roleCode)
            .withClaim("branch_ids", branchIds)
            .withClaim("jti", UUID.randomUUID().toString())
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiry));
        
        if (currentBranchId != null) {
            builder.withClaim("current_branch_id", currentBranchId);
        }
        
        return builder.sign(algorithm);
    }

    @Override
    public TokenPayload verifyToken(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            
            Integer userId = Integer.parseInt(jwt.getSubject());
            String roleCode = jwt.getClaim("role").asString();
            
            // Parse branch_ids từ claim
            List<Integer> branchIds = jwt.getClaim("branch_ids").asList(Integer.class);
            
            Integer currentBranchId = null;
            if (!jwt.getClaim("current_branch_id").isNull()) {
                currentBranchId = jwt.getClaim("current_branch_id").asInt();
            }
            
            String jti = jwt.getClaim("jti").asString();
            
            return new TokenPayload(userId, roleCode, branchIds, currentBranchId, jti);
            
        } catch (JWTVerificationException e) {
            throw new TokenInvalidException("Invalid or expired token", e);
        } catch (Exception e) {
            throw new TokenInvalidException("Failed to parse token", e);
        }
    }
}

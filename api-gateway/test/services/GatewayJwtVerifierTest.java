package services;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayJwtVerifierTest {
    private static final String SECRET = "a-development-secret-longer-than-32-characters";

    @Test
    void verifiesIdentityAndBranchClaims() {
        GatewayJwtVerifier verifier = new GatewayJwtVerifier(config(SECRET));

        GatewayJwtVerifier.Claims claims = verifier.verify(token(SECRET));

        assertThat(claims.getUserId()).isEqualTo(42);
        assertThat(claims.getRole()).isEqualTo("SP");
        assertThat(claims.getBranchIds()).containsExactly(3, 7);
        assertThat(claims.getCurrentBranchId()).isEqualTo(3);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        GatewayJwtVerifier verifier = new GatewayJwtVerifier(config(SECRET));

        assertThatThrownBy(() -> verifier.verify(token("another-secret-longer-than-32-characters")))
                .isInstanceOf(RuntimeException.class);
    }

    private String token(String secret) {
        return JWT.create()
                .withSubject("42")
                .withClaim("role", "SP")
                .withClaim("branch_ids", List.of(3, 7))
                .withClaim("current_branch_id", 3)
                .withExpiresAt(Date.from(Instant.now().plusSeconds(120)))
                .sign(Algorithm.HMAC256(secret));
    }

    private Config config(String secret) {
        return ConfigFactory.parseString(
                "jwt.secret=\"" + secret + "\"\n"
                        + "jwt.clock-skew-seconds=0");
    }
}

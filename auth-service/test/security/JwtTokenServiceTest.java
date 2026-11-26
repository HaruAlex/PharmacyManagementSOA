package security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;
import play.Environment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenServiceTest {
    private static final Instant START = Instant.parse("2026-11-26T00:00:00Z");

    @Test
    void createAccessToken_shouldUseInjectedClockForIssueAndExpiry() {
        MutableClock clock = new MutableClock(START);
        JwtTokenService service = service(clock);

        String token = service.createAccessToken(12, "AD", List.of(1, 2), 1);
        DecodedJWT decoded = JWT.decode(token);

        assertThat(decoded.getIssuedAt().toInstant()).isEqualTo(START);
        assertThat(decoded.getExpiresAt().toInstant()).isEqualTo(START.plus(Duration.ofMinutes(2)));
        assertThat(service.verifyToken(token).getUserId()).isEqualTo(12);
    }

    @Test
    void verifyToken_afterInjectedClockPassesExpiry_shouldRejectToken() {
        MutableClock clock = new MutableClock(START);
        JwtTokenService service = service(clock);
        String token = service.createAccessToken(12, "AD", List.of(1), null);

        clock.advance(Duration.ofMinutes(3));

        assertThatThrownBy(() -> service.verifyToken(token))
                .isInstanceOf(TokenInvalidException.class);
    }

    private JwtTokenService service(Clock clock) {
        Config config = ConfigFactory.parseString(
                "jwt.secret=\"unit-test-secret-key-that-is-long-enough\"\n"
                        + "jwt.accessTokenMinutes=2");
        Environment environment = mock(Environment.class);
        when(environment.isProd()).thenReturn(false);
        return new JwtTokenService(config, environment, clock);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

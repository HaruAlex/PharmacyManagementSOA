package services;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {
    @Test
    void tryAcquire_withRepeatedIdentity_shouldBlockAfterIdentityLimit() {
        RateLimitService service = new RateLimitService(
                Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC));

        assertThat(service.tryAcquire("LOGIN", "127.0.0.1", "demo", 2, 10, Duration.ofMinutes(1)))
                .isTrue();
        assertThat(service.tryAcquire("LOGIN", "127.0.0.1", "demo", 2, 10, Duration.ofMinutes(1)))
                .isTrue();
        assertThat(service.tryAcquire("LOGIN", "127.0.0.1", "demo", 2, 10, Duration.ofMinutes(1)))
                .isFalse();
    }

    @Test
    void tryAcquire_withDifferentIdentities_shouldStillApplyIpLimit() {
        RateLimitService service = new RateLimitService(
                Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC));

        assertThat(service.tryAcquire("REFRESH", "127.0.0.1", "token-1", 10, 2, Duration.ofMinutes(1)))
                .isTrue();
        assertThat(service.tryAcquire("REFRESH", "127.0.0.1", "token-2", 10, 2, Duration.ofMinutes(1)))
                .isTrue();
        assertThat(service.tryAcquire("REFRESH", "127.0.0.1", "token-3", 10, 2, Duration.ofMinutes(1)))
                .isFalse();
    }

    @Test
    void tryAcquire_afterWindowExpires_shouldAllowAgain() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-26T00:00:00Z"));
        RateLimitService service = new RateLimitService(clock);

        assertThat(service.tryAcquire("LOGIN", "127.0.0.1", "demo", 1, 1, Duration.ofMinutes(1)))
                .isTrue();
        assertThat(service.tryAcquire("LOGIN", "127.0.0.1", "demo", 1, 1, Duration.ofMinutes(1)))
                .isFalse();

        clock.advance(Duration.ofMinutes(1));

        assertThat(service.tryAcquire("LOGIN", "127.0.0.1", "demo", 1, 1, Duration.ofMinutes(1)))
                .isTrue();
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

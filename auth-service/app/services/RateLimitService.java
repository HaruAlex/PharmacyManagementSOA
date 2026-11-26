package services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class RateLimitService {
    private final Clock clock;
    private final Cache<String, Window> windows = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofHours(24))
            .build();

    @Inject
    public RateLimitService(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(
            String scope,
            String clientIp,
            String identity,
            int maxPerIdentity,
            int maxPerIp,
            Duration window) {
        if (scope == null || scope.isBlank() || clientIp == null || identity == null) {
            throw new IllegalArgumentException("Rate limit scope, client IP, and identity are required");
        }
        if (maxPerIdentity < 1 || maxPerIp < 1 || window == null
                || window.isZero() || window.isNegative()
                || window.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("Rate limit thresholds and window are invalid");
        }

        Instant now = clock.instant();
        String normalizedIp = clientIp.isBlank() ? "unknown" : clientIp.trim();
        boolean ipAllowed = record(
                digest(scope + "\u0000ip\u0000" + normalizedIp),
                maxPerIp,
                window,
                now);
        boolean identityAllowed = record(
                digest(scope + "\u0000identity\u0000" + normalizedIp + "\u0000" + identity.trim()),
                maxPerIdentity,
                window,
                now);
        return ipAllowed && identityAllowed;
    }

    private boolean record(String key, int limit, Duration window, Instant now) {
        AtomicBoolean allowed = new AtomicBoolean();
        windows.asMap().compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.resetAt)) {
                allowed.set(true);
                return new Window(now.plus(window), 1);
            }
            if (current.count >= limit) {
                allowed.set(false);
                return current;
            }
            current.count++;
            allowed.set(true);
            return current;
        });
        return allowed.get();
    }

    private String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static final class Window {
        private final Instant resetAt;
        private int count;

        private Window(Instant resetAt, int count) {
            this.resetAt = resetAt;
            this.count = count;
        }
    }
}

package app.cairn.api.auth.ratelimit;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Fixed-window login rate limit keyed by {@code (ip, email)} — default 5 attempts per 60s (arch §7).
 *
 * <p>Prefers a Valkey/Redis counter (shared across replicas at commercialization) and degrades to an
 * in-memory counter when Redis is unavailable (dev, or a transient outage) via a short circuit-breaker
 * so a Redis outage never blocks logins or adds per-request latency.
 */
@Component
public class LoginRateLimiter {

    private static final long CIRCUIT_COOLDOWN_MS = 30_000;
    private static final int MEMORY_MAX_ENTRIES = 50_000;

    @Nullable private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int maxAttempts;
    private final int windowSeconds;

    private final ConcurrentHashMap<String, Window> memory = new ConcurrentHashMap<>();
    private volatile long redisUnhealthyUntil = 0L;

    public LoginRateLimiter(
            @Nullable StringRedisTemplate redis,
            @Value("${cairn.auth.ratelimit.enabled}") boolean enabled,
            @Value("${cairn.auth.ratelimit.max-attempts}") int maxAttempts,
            @Value("${cairn.auth.ratelimit.window-seconds}") int windowSeconds) {
        this.redis = redis;
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    /** Record an attempt and return true if it is still within the allowed window budget. */
    public boolean tryAcquire(String ip, String email) {
        if (!enabled) {
            return true;
        }
        String key = "cairn:rl:login:" + ip + ":" + email.toLowerCase(Locale.ROOT);
        Boolean viaRedis = tryRedis(key);
        return viaRedis != null ? viaRedis : tryMemory(key);
    }

    @Nullable
    private Boolean tryRedis(String key) {
        if (redis == null || System.currentTimeMillis() < redisUnhealthyUntil) {
            return null;
        }
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
            return count == null || count <= maxAttempts;
        } catch (RuntimeException e) {
            redisUnhealthyUntil = System.currentTimeMillis() + CIRCUIT_COOLDOWN_MS;
            return null;
        }
    }

    private boolean tryMemory(String key) {
        if (memory.size() > MEMORY_MAX_ENTRIES) {
            long now = System.currentTimeMillis();
            memory.values().removeIf(w -> now >= w.resetAt);
        }
        long now = System.currentTimeMillis();
        Window w = memory.compute(key, (k, cur) -> {
            if (cur == null || now >= cur.resetAt) {
                return new Window(1, now + windowSeconds * 1000L);
            }
            return new Window(cur.count + 1, cur.resetAt);
        });
        return w.count <= maxAttempts;
    }

    private record Window(int count, long resetAt) {}
}

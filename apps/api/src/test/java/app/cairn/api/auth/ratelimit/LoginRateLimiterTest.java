package app.cairn.api.auth.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    /** No Redis template → pure in-memory fixed window. */
    private LoginRateLimiter memoryLimiter(int max, int window) {
        return new LoginRateLimiter(null, true, max, window);
    }

    @Test
    void allowsUpToMaxThenBlocks() {
        LoginRateLimiter limiter = memoryLimiter(3, 60);
        assertThat(limiter.tryAcquire("1.1.1.1", "a@b.com")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1", "a@b.com")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1", "a@b.com")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1", "a@b.com")).isFalse();
    }

    @Test
    void keysAreScopedByIpAndEmail() {
        LoginRateLimiter limiter = memoryLimiter(1, 60);
        assertThat(limiter.tryAcquire("1.1.1.1", "a@b.com")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1", "a@b.com")).isFalse();
        // Different email, same ip → independent budget.
        assertThat(limiter.tryAcquire("1.1.1.1", "other@b.com")).isTrue();
        // Different ip, same email → independent budget.
        assertThat(limiter.tryAcquire("2.2.2.2", "a@b.com")).isTrue();
    }

    @Test
    void emailIsCaseInsensitive() {
        LoginRateLimiter limiter = memoryLimiter(1, 60);
        assertThat(limiter.tryAcquire("1.1.1.1", "Ada@Acme.Test")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1", "ada@acme.test")).isFalse();
    }

    @Test
    void disabledLimiterAlwaysAllows() {
        LoginRateLimiter limiter = new LoginRateLimiter(null, false, 1, 60);
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("1.1.1.1", "a@b.com")).isTrue();
        }
    }

    @Test
    void newWindowResetsBudget() throws InterruptedException {
        LoginRateLimiter limiter = memoryLimiter(1, 1); // 1s window
        assertThat(limiter.tryAcquire("1.1.1.1", "a@b.com")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1", "a@b.com")).isFalse();
        Thread.sleep(1100);
        assertThat(limiter.tryAcquire("1.1.1.1", "a@b.com")).isTrue();
    }
}

package co.za.tveco.bff.security;

import co.za.tveco.bff.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AuthRateLimiter {

    private final int loginMaxAttempts;
    private final long loginWindowMs;
    private final int signupMaxAttempts;
    private final long signupWindowMs;

    private final ConcurrentHashMap<String, FixedWindowCounter> counters = new ConcurrentHashMap<>();

    public AuthRateLimiter(
            @Value("${app.auth.rate-limit.login.max-attempts:12}") int loginMaxAttempts,
            @Value("${app.auth.rate-limit.login.window-seconds:300}") int loginWindowSeconds,
            @Value("${app.auth.rate-limit.signup.max-attempts:6}") int signupMaxAttempts,
            @Value("${app.auth.rate-limit.signup.window-seconds:900}") int signupWindowSeconds
    ) {
        this.loginMaxAttempts = loginMaxAttempts;
        this.loginWindowMs = Math.max(1, loginWindowSeconds) * 1000L;
        this.signupMaxAttempts = signupMaxAttempts;
        this.signupWindowMs = Math.max(1, signupWindowSeconds) * 1000L;
    }

    public void assertLoginAllowed(String clientIp, String email) {
        String key = "login:" + normalize(clientIp) + ":" + normalize(email);
        if (!tryConsume(key, loginMaxAttempts, loginWindowMs)) {
            throw new TooManyRequestsException("Too many login attempts. Please wait a few minutes and try again.");
        }
    }

    public void assertSignupAllowed(String clientIp, String email) {
        String key = "signup:" + normalize(clientIp) + ":" + normalize(email);
        if (!tryConsume(key, signupMaxAttempts, signupWindowMs)) {
            throw new TooManyRequestsException("Too many signup attempts. Please wait a few minutes and try again.");
        }
    }

    private boolean tryConsume(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        FixedWindowCounter counter = counters.computeIfAbsent(key, ignored -> new FixedWindowCounter(now));

        synchronized (counter) {
            if (now - counter.windowStartMs >= windowMs) {
                counter.windowStartMs = now;
                counter.count.set(0);
            }

            int newCount = counter.count.incrementAndGet();
            return newCount <= limit;
        }
    }

    private String normalize(String value) {
        return value == null ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class FixedWindowCounter {
        private volatile long windowStartMs;
        private final AtomicInteger count = new AtomicInteger(0);

        private FixedWindowCounter(long windowStartMs) {
            this.windowStartMs = windowStartMs;
        }
    }
}

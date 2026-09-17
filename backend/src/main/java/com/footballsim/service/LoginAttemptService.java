package com.footballsim.service;

import com.footballsim.exception.RateLimitException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final long WINDOW_MILLIS = 10 * 60 * 1000L;

    private record AttemptRecord(int count, Instant windowStart) {}

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public void checkNotBlocked(String email) {
        AttemptRecord record = attempts.get(normalize(email));
        if (record == null) return;
        if (isExpired(record)) {
            attempts.remove(normalize(email));
            return;
        }
        if (record.count() >= MAX_ATTEMPTS) {
            throw new RateLimitException("Too many failed login attempts. Please try again later.");
        }
    }

    public void loginFailed(String email) {
        String key = normalize(email);
        attempts.compute(key, (k, existing) -> {
            if (existing == null || isExpired(existing)) {
                return new AttemptRecord(1, Instant.now());
            }
            return new AttemptRecord(existing.count() + 1, existing.windowStart());
        });
    }

    public void loginSucceeded(String email) {
        attempts.remove(normalize(email));
    }

    private boolean isExpired(AttemptRecord record) {
        return Instant.now().toEpochMilli() - record.windowStart().toEpochMilli() > WINDOW_MILLIS;
    }

    private String normalize(String email) {
        return email == null ? "" : email.toLowerCase();
    }
}

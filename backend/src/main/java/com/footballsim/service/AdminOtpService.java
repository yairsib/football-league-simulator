package com.footballsim.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory admin OTP sessions.
 * Codes are stored hashed (BCrypt). Raw codes are never logged or returned via API.
 * Sessions expire after 5 minutes; limited to 3 wrong attempts.
 */
@Service
public class AdminOtpService {

    private static final long EXPIRY_SECONDS = 300;
    private static final int MAX_ATTEMPTS = 3;

    private record OtpSession(Long userId, String hashedCode, Instant expiresAt, int failedAttempts) {
        OtpSession withFailedAttempt() {
            return new OtpSession(userId, hashedCode, expiresAt, failedAttempts + 1);
        }
    }

    private final ConcurrentHashMap<String, OtpSession> sessions = new ConcurrentHashMap<>();
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public AdminOtpService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates an OTP session for the given admin user.
     * Returns the raw 6-digit code to be sent by email — caller must not log it.
     */
    public String createSession(Long userId) {
        removeExpiredSessions();
        // Invalidate any previous session for this user
        sessions.values().removeIf(s -> s.userId().equals(userId));

        String rawCode = generateCode();
        String token = UUID.randomUUID().toString();
        sessions.put(token, new OtpSession(
                userId,
                passwordEncoder.encode(rawCode),
                Instant.now().plusSeconds(EXPIRY_SECONDS),
                0
        ));
        return rawCode + "|" + token; // callers split on "|" to get code and token separately
    }

    /**
     * Verifies the code for the given admin verification token.
     * Returns the userId on success. Throws on failure (invalid/expired/too many attempts).
     * The session is consumed (deleted) on success.
     */
    public Long verify(String token, String code) {
        OtpSession session = sessions.get(token);
        if (session == null || Instant.now().isAfter(session.expiresAt())) {
            sessions.remove(token);
            throw new IllegalArgumentException("Invalid or expired verification code.");
        }
        if (session.failedAttempts() >= MAX_ATTEMPTS) {
            sessions.remove(token);
            throw new IllegalArgumentException("Too many failed attempts. Please log in again.");
        }
        if (!passwordEncoder.matches(code, session.hashedCode())) {
            OtpSession updated = session.withFailedAttempt();
            if (updated.failedAttempts() >= MAX_ATTEMPTS) {
                sessions.remove(token);
            } else {
                sessions.put(token, updated);
            }
            throw new IllegalArgumentException("Invalid verification code.");
        }
        sessions.remove(token);
        return session.userId();
    }

    /** Removes the session for a token (called when email send fails). */
    public void invalidate(String token) {
        sessions.remove(token);
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private void removeExpiredSessions() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, OtpSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            if (now.isAfter(it.next().getValue().expiresAt())) it.remove();
        }
    }
}

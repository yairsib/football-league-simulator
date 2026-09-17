package com.footballsim.service;

import com.footballsim.entity.User;
import com.footballsim.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory password reset sessions.
 * Codes are stored hashed. Raw codes are never logged or returned via API.
 * Sessions expire after 10 minutes; limited to 5 wrong attempts.
 */
@Service
public class PasswordResetService {

    private static final long EXPIRY_SECONDS = 600;
    private static final int MAX_ATTEMPTS = 5;

    private record ResetSession(Long userId, String hashedCode, Instant expiresAt, int failedAttempts) {
        ResetSession withFailedAttempt() {
            return new ResetSession(userId, hashedCode, expiresAt, failedAttempts + 1);
        }
    }

    private final ConcurrentHashMap<String, ResetSession> sessions = new ConcurrentHashMap<>();
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a reset session for the given user.
     * Returns the raw 6-digit code (to be sent by email) and opaque token in the format "code|token".
     * Caller must not log the code.
     */
    public String createSession(Long userId) {
        removeExpiredSessions();
        // Invalidate any previous session for this user
        sessions.values().removeIf(s -> s.userId().equals(userId));

        String rawCode = generateCode();
        String token = UUID.randomUUID().toString();
        sessions.put(token, new ResetSession(
                userId,
                passwordEncoder.encode(rawCode),
                Instant.now().plusSeconds(EXPIRY_SECONDS),
                0
        ));
        return rawCode + "|" + token;
    }

    /**
     * Verifies the code and resets the password for the given token.
     * Throws on failure (invalid/expired/too many attempts/policy violation).
     * The session is consumed (deleted) on success.
     */
    @Transactional
    public void resetPassword(String token, String code, String newPassword) {
        ResetSession session = sessions.get(token);
        if (session == null || Instant.now().isAfter(session.expiresAt())) {
            sessions.remove(token);
            throw new IllegalArgumentException("Invalid or expired reset code.");
        }
        if (session.failedAttempts() >= MAX_ATTEMPTS) {
            sessions.remove(token);
            throw new IllegalArgumentException("Too many failed attempts. Please request a new reset code.");
        }
        if (!passwordEncoder.matches(code, session.hashedCode())) {
            ResetSession updated = session.withFailedAttempt();
            if (updated.failedAttempts() >= MAX_ATTEMPTS) {
                sessions.remove(token);
            } else {
                sessions.put(token, updated);
            }
            throw new IllegalArgumentException("Invalid reset code.");
        }

        User user = userRepository.findById(session.userId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset code."));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        sessions.remove(token);
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
        Iterator<Map.Entry<String, ResetSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            if (now.isAfter(it.next().getValue().expiresAt())) it.remove();
        }
    }
}

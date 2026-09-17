package com.footballsim.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test-only email service. Active ONLY when both:
 *   1. Spring profile = "test"   (SPRING_PROFILES_ACTIVE=test)
 *   2. app.otp.test-mode = true
 *
 * Stores sent codes in memory for retrieval by TestMailController.
 * Never used in normal/dev/prod runtime.
 */
@Service
@Primary
@Profile("test")
@ConditionalOnProperty(name = "app.otp.test-mode", havingValue = "true")
public class FakeEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(FakeEmailService.class);

    public record SentEmail(String to, String subject, String code, String type) {}

    private final List<SentEmail> sentEmails = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void sendAdminOtp(String to, String code) {
        log.info("[TEST] Admin OTP email queued for {} (code not logged for security)", to);
        sentEmails.add(new SentEmail(to, "Your admin verification code", code, "ADMIN_OTP"));
    }

    @Override
    public void sendPasswordReset(String to, String code) {
        log.info("[TEST] Password reset email queued for {} (code not logged for security)", to);
        sentEmails.add(new SentEmail(to, "Your password reset code", code, "PASSWORD_RESET"));
    }

    public List<SentEmail> getSentEmails() {
        return Collections.unmodifiableList(sentEmails);
    }

    public void clearEmails() {
        sentEmails.clear();
    }
}

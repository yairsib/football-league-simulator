package com.footballsim.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Active when app.mail.enabled=false (the default).
 * Refuses to deliver any code — throws a safe configuration exception.
 * No OTP or reset codes are logged or exposed.
 */
@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailService.class);

    @Override
    public void sendAdminOtp(String to, String code) {
        log.warn("Email not configured (app.mail.enabled=false). Admin OTP cannot be delivered to {}.", to);
        throw new IllegalStateException("Email delivery is not configured. Admin login is unavailable.");
    }

    @Override
    public void sendPasswordReset(String to, String code) {
        log.warn("Email not configured (app.mail.enabled=false). Password reset cannot be delivered to {}.", to);
        throw new IllegalStateException("Email delivery is not configured. Password reset is unavailable.");
    }
}

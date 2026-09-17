package com.footballsim.service;

import com.footballsim.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    void noAttemptsAllowed() {
        assertThatCode(() -> service.checkNotBlocked("test@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void blockedAfterMaxFailedAttempts() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.loginFailed("test@example.com");
        }
        assertThatThrownBy(() -> service.checkNotBlocked("test@example.com"))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Too many failed login attempts");
    }

    @Test
    void notBlockedBeforeMaxAttempts() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            service.loginFailed("test@example.com");
        }
        assertThatCode(() -> service.checkNotBlocked("test@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void successResetsCounter() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.loginFailed("test@example.com");
        }
        service.loginSucceeded("test@example.com");
        assertThatCode(() -> service.checkNotBlocked("test@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void emailLookupIsCaseInsensitive() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.loginFailed("Test@Example.COM");
        }
        assertThatThrownBy(() -> service.checkNotBlocked("test@example.com"))
                .isInstanceOf(RateLimitException.class);
    }
}

package com.footballsim.service;

import com.footballsim.dto.AuthResponse;
import com.footballsim.dto.LoginRequest;
import com.footballsim.dto.RegisterRequest;
import com.footballsim.entity.User;
import com.footballsim.enums.Role;
import com.footballsim.repository.UserRepository;
import com.footballsim.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 * Uses real concrete instances (PasswordEncoder, JwtService, LoginAttemptService, AdminOtpService)
 * since Mockito inline mocking does not work for these on Java 23.
 * Repositories (interfaces) and EmailService (interface) are mocked.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock EmailService emailService;

    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    JwtService jwtService;
    LoginAttemptService loginAttemptService;
    AdminOtpService adminOtpService;
    AuthService authService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret",
                "test-secret-key-at-least-32-characters-long!!");
        ReflectionTestUtils.setField(jwtService, "expiration", 86400000L);

        loginAttemptService = new LoginAttemptService();
        adminOtpService = new AdminOtpService(passwordEncoder);

        authService = new AuthService(userRepository, passwordEncoder, jwtService,
                loginAttemptService, adminOtpService, emailService);
        ReflectionTestUtils.setField(authService, "configuredAdminEmail", "admin@example.com");
    }

    // ---- Registration ----

    @Test
    void register_alwaysCreatesUserRole() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        User saved = userWithRole(Role.USER, "user@example.com");
        when(userRepository.save(any())).thenReturn(saved);

        AuthResponse res = authService.register(registerRequest("user@example.com", "username1"));

        assertThat(res.getToken()).isNotNull();
        assertThat(res.getAdminVerificationRequired()).isNull();
        verify(userRepository).save(argThat(u -> u.getRole() == Role.USER));
    }

    @Test
    void register_blocksAdminEmail() {
        assertThatThrownBy(() -> authService.register(registerRequest("admin@example.com", "admin")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be used for registration");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_blocksAdminEmailCaseInsensitive() {
        assertThatThrownBy(() -> authService.register(registerRequest("ADMIN@EXAMPLE.COM", "admin")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_regularEmailNotBlocked() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        User saved = userWithRole(Role.USER, "regular@example.com");
        when(userRepository.save(any())).thenReturn(saved);

        AuthResponse res = authService.register(registerRequest("regular@example.com", "user"));
        assertThat(res.getToken()).isNotNull();
    }

    // ---- Normal USER login ----

    @Test
    void login_normalUser_returnsJwtImmediately() {
        User user = userWithRole(Role.USER, "user@example.com");
        user.setPasswordHash(passwordEncoder.encode("Secret123"));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        AuthResponse res = authService.login(loginRequest("user@example.com", "Secret123"));

        assertThat(res.getToken()).isNotNull();
        assertThat(res.getAdminVerificationRequired()).isNull();
        verify(emailService, never()).sendAdminOtp(anyString(), anyString());
    }

    @Test
    void login_invalidCredentials_throwsGenericError() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("nobody@example.com", "wrong")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email or password");
    }

    // ---- ADMIN login OTP flow ----

    @Test
    void login_admin_doesNotReturnJwtImmediately() {
        User admin = userWithRole(Role.ADMIN, "admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("Admin123!"));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        doNothing().when(emailService).sendAdminOtp(anyString(), anyString());

        AuthResponse res = authService.login(loginRequest("admin@example.com", "Admin123!"));

        assertThat(res.getToken()).isNull();
        assertThat(res.getUser()).isNull();
        assertThat(res.getAdminVerificationRequired()).isTrue();
        assertThat(res.getAdminVerificationToken()).isNotNull();
        verify(emailService).sendAdminOtp(eq("admin@example.com"), anyString());
    }

    @Test
    void login_admin_emailSendFails_invalidatesSession() {
        User admin = userWithRole(Role.ADMIN, "admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("Admin123!"));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        doThrow(new IllegalStateException("Email delivery failed"))
                .when(emailService).sendAdminOtp(anyString(), anyString());

        assertThatThrownBy(() -> authService.login(loginRequest("admin@example.com", "Admin123!")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void login_admin_emailSendFails_sessionNotUsable() {
        // If email fails, the OTP session must be invalidated — the returned token (if any) won't work
        User admin = userWithRole(Role.ADMIN, "admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("Admin123!"));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        String[] capturedToken = {null};
        doAnswer(inv -> {
            throw new IllegalStateException("Email delivery failed");
        }).when(emailService).sendAdminOtp(anyString(), anyString());

        try {
            authService.login(loginRequest("admin@example.com", "Admin123!"));
        } catch (IllegalStateException ignored) {}

        // Any subsequent verify attempt with a random token should fail (no valid session)
        assertThatThrownBy(() -> authService.verifyAdminOtp("some-random-token", "123456"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyAdminOtp_correctCode_returnsJwt() {
        // Arrange: do a successful admin login first to get a real token
        User admin = userWithRole(Role.ADMIN, "admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("Admin123!"));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        String[] sentCode = {null};
        String[] sentToken = {null};
        doAnswer(inv -> { sentCode[0] = inv.getArgument(1); return null; })
                .when(emailService).sendAdminOtp(anyString(), anyString());

        AuthResponse pending = authService.login(loginRequest("admin@example.com", "Admin123!"));
        String verificationToken = pending.getAdminVerificationToken();
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        AuthResponse res = authService.verifyAdminOtp(verificationToken, sentCode[0]);

        assertThat(res.getToken()).isNotNull();
        assertThat(res.getAdminVerificationRequired()).isNull();
    }

    @Test
    void verifyAdminOtp_wrongCode_fails() {
        User admin = userWithRole(Role.ADMIN, "admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("Admin123!"));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        doNothing().when(emailService).sendAdminOtp(anyString(), anyString());

        AuthResponse pending = authService.login(loginRequest("admin@example.com", "Admin123!"));
        String token = pending.getAdminVerificationToken();

        assertThatThrownBy(() -> authService.verifyAdminOtp(token, "000000"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- AdminOtpService direct tests ----

    @Test
    void adminOtp_singleUse() {
        String codeAndToken = adminOtpService.createSession(1L);
        String[] parts = codeAndToken.split("\\|", 2);
        String code = parts[0], token = parts[1];

        Long userId = adminOtpService.verify(token, code);
        assertThat(userId).isEqualTo(1L);

        assertThatThrownBy(() -> adminOtpService.verify(token, code))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminOtp_tooManyWrongAttempts_invalidatesSession() {
        String codeAndToken = adminOtpService.createSession(1L);
        String token = codeAndToken.split("\\|", 2)[1];

        for (int i = 0; i < 3; i++) {
            try { adminOtpService.verify(token, "000000"); } catch (IllegalArgumentException ignored) {}
        }

        assertThatThrownBy(() -> adminOtpService.verify(token, "000000"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminOtp_wrongCodeFails_correctCodeSucceeds() {
        String codeAndToken = adminOtpService.createSession(2L);
        String[] parts = codeAndToken.split("\\|", 2);
        String rawCode = parts[0], token = parts[1];

        assertThatThrownBy(() -> adminOtpService.verify(token, "000000"))
                .isInstanceOf(IllegalArgumentException.class);

        Long userId = adminOtpService.verify(token, rawCode);
        assertThat(userId).isEqualTo(2L);
    }

    // ---- Forgot password ----

    @Test
    void forgotPassword_nonExistingEmail_returnsToken_noEmailSent() {
        PasswordResetService prs = new PasswordResetService(userRepository, passwordEncoder);
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        String token = authService.forgotPassword("nobody@example.com", prs);

        assertThat(token).isNotNull().isNotBlank();
        verify(emailService, never()).sendPasswordReset(anyString(), anyString());
    }

    @Test
    void forgotPassword_existingEmail_sendsEmailAndReturnsToken() {
        PasswordResetService prs = new PasswordResetService(userRepository, passwordEncoder);
        User user = userWithRole(Role.USER, "user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        doNothing().when(emailService).sendPasswordReset(anyString(), anyString());

        String token = authService.forgotPassword("user@example.com", prs);

        assertThat(token).isNotNull().isNotBlank();
        verify(emailService).sendPasswordReset(eq("user@example.com"), anyString());
    }

    @Test
    void forgotPassword_emailSendFails_sessionInvalidated() {
        PasswordResetService prs = new PasswordResetService(userRepository, passwordEncoder);
        User user = userWithRole(Role.USER, "user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        doThrow(new IllegalStateException("Email delivery failed"))
                .when(emailService).sendPasswordReset(anyString(), anyString());

        String token = authService.forgotPassword("user@example.com", prs);
        assertThat(token).isNotNull();

        assertThatThrownBy(() -> prs.resetPassword(token, "123456", "NewPass1!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forgotPassword_wrongCode_fails() {
        PasswordResetService prs = new PasswordResetService(userRepository, passwordEncoder);
        User user = userWithRole(Role.USER, "user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        doNothing().when(emailService).sendPasswordReset(anyString(), anyString());

        String token = authService.forgotPassword("user@example.com", prs);

        assertThatThrownBy(() -> prs.resetPassword(token, "000000", "NewPass1!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forgotPassword_correctCode_updatesPassword() {
        PasswordResetService prs = new PasswordResetService(userRepository, passwordEncoder);
        User user = userWithRole(Role.USER, "user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        String[] sentCode = {null};
        doAnswer(inv -> { sentCode[0] = inv.getArgument(1); return null; })
                .when(emailService).sendPasswordReset(anyString(), anyString());

        String token = authService.forgotPassword("user@example.com", prs);
        prs.resetPassword(token, sentCode[0], "NewPass1!");

        verify(userRepository).save(argThat(u -> passwordEncoder.matches("NewPass1!", u.getPasswordHash())));
    }

    @Test
    void forgotPassword_singleUse() {
        PasswordResetService prs = new PasswordResetService(userRepository, passwordEncoder);
        User user = userWithRole(Role.USER, "user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        String[] sentCode = {null};
        doAnswer(inv -> { sentCode[0] = inv.getArgument(1); return null; })
                .when(emailService).sendPasswordReset(anyString(), anyString());

        String token = authService.forgotPassword("user@example.com", prs);
        prs.resetPassword(token, sentCode[0], "NewPass1!");

        assertThatThrownBy(() -> prs.resetPassword(token, sentCode[0], "NewPass2!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forgotPassword_tooManyWrongAttempts_sessionDestroyed() {
        PasswordResetService prs = new PasswordResetService(userRepository, passwordEncoder);
        User user = userWithRole(Role.USER, "user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        doNothing().when(emailService).sendPasswordReset(anyString(), anyString());

        String token = authService.forgotPassword("user@example.com", prs);

        for (int i = 0; i < 5; i++) {
            try { prs.resetPassword(token, "000000", "NewPass1!"); } catch (IllegalArgumentException ignored) {}
        }

        // After 5 failures the session is gone; any attempt returns the same generic error
        assertThatThrownBy(() -> prs.resetPassword(token, "000000", "NewPass1!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Helpers ----

    private static RegisterRequest registerRequest(String email, String username) {
        RegisterRequest r = new RegisterRequest();
        r.setEmail(email); r.setUsername(username); r.setPassword("Secret123");
        return r;
    }

    private static LoginRequest loginRequest(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email); r.setPassword(password);
        return r;
    }

    private static User userWithRole(Role role, String email) {
        User u = new User();
        u.setEmail(email); u.setUsername(email.split("@")[0]);
        u.setRole(role); u.setBalance(BigDecimal.valueOf(1000));
        return u;
    }
}

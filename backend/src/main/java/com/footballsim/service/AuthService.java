package com.footballsim.service;

import com.footballsim.dto.AuthResponse;
import com.footballsim.dto.LoginRequest;
import com.footballsim.dto.RegisterRequest;
import com.footballsim.dto.UserResponse;
import com.footballsim.entity.User;
import com.footballsim.enums.Role;
import com.footballsim.repository.UserRepository;
import com.footballsim.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final AdminOtpService adminOtpService;
    private final EmailService emailService;

    @Value("${app.admin.email:}")
    private String configuredAdminEmail;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       LoginAttemptService loginAttemptService,
                       AdminOtpService adminOtpService,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.adminOtpService = adminOtpService;
        this.emailService = emailService;
    }

    /**
     * Public registration — always creates USER role.
     * Registration with the configured admin email is blocked with a generic error
     * to prevent public users from attempting to claim an admin identity.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Block registration with the configured admin email (no role enumeration in message)
        if (configuredAdminEmail != null && !configuredAdminEmail.isBlank()
                && request.getEmail().equalsIgnoreCase(configuredAdminEmail)) {
            throw new IllegalArgumentException("This email address cannot be used for registration.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setBalance(new BigDecimal("1000.00"));
        user.setRole(Role.USER); // always USER; ADMIN is assigned only by AdminBootstrapService

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved.getEmail());
        return new AuthResponse(token, UserResponse.from(saved));
    }

    /**
     * Login flow:
     * - USER with valid credentials → returns JWT immediately.
     * - ADMIN with valid credentials → generates OTP, sends email, returns pending response.
     *   If email sending fails, OTP session is invalidated and a safe error is thrown.
     * - Invalid credentials → generic error (no role/email enumeration).
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        loginAttemptService.checkNotBlocked(request.getEmail());

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptService.loginFailed(request.getEmail());
            throw new IllegalArgumentException("Invalid email or password");
        }

        loginAttemptService.loginSucceeded(request.getEmail());

        if (user.getRole() == Role.ADMIN) {
            return initiateAdminOtp(user);
        }

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, UserResponse.from(user));
    }

    /**
     * Verifies the admin OTP and returns a JWT if valid.
     * Throws on invalid/expired/too-many-attempts.
     */
    @Transactional
    public AuthResponse verifyAdminOtp(String adminVerificationToken, String code) {
        Long userId = adminOtpService.verify(adminVerificationToken, code);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification session."));
        String jwt = jwtService.generateToken(user.getEmail());
        return new AuthResponse(jwt, UserResponse.from(user));
    }

    /**
     * Forgot-password: always returns a generic success to avoid account enumeration.
     * Returns an opaque resetToken to pass back on the reset step.
     * If the email does not exist, or email delivery fails, no usable session is created.
     */
    @Transactional
    public String forgotPassword(String email, PasswordResetService passwordResetService) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return java.util.UUID.randomUUID().toString(); // fake token — no session created
        }

        String codeAndToken = passwordResetService.createSession(user.getId());
        String[] parts = codeAndToken.split("\\|", 2);
        String rawCode = parts[0];
        String resetToken = parts[1];

        try {
            emailService.sendPasswordReset(user.getEmail(), rawCode);
        } catch (Exception e) {
            log.warn("Failed to send password reset email for user {}: {}. Session invalidated.", user.getId(), e.getMessage());
            passwordResetService.invalidate(resetToken);
            return java.util.UUID.randomUUID().toString(); // fake token — session invalidated
        }

        return resetToken;
    }

    private AuthResponse initiateAdminOtp(User admin) {
        String codeAndToken = adminOtpService.createSession(admin.getId());
        String[] parts = codeAndToken.split("\\|", 2);
        String rawCode = parts[0];
        String verificationToken = parts[1];

        try {
            emailService.sendAdminOtp(admin.getEmail(), rawCode);
        } catch (Exception e) {
            log.warn("Failed to send admin OTP for user {}: {}. Session invalidated.", admin.getId(), e.getMessage());
            adminOtpService.invalidate(verificationToken);
            throw new IllegalStateException("Admin verification email could not be sent. Please try again later.");
        }

        return AuthResponse.adminPending(verificationToken);
    }
}

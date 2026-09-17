package com.footballsim.controller;

import com.footballsim.dto.AdminVerifyRequest;
import com.footballsim.dto.AuthResponse;
import com.footballsim.dto.ForgotPasswordRequest;
import com.footballsim.dto.LoginRequest;
import com.footballsim.dto.MessageResponse;
import com.footballsim.dto.RegisterRequest;
import com.footballsim.dto.ResetPasswordRequest;
import com.footballsim.service.AuthService;
import com.footballsim.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String FORGOT_PASSWORD_GENERIC =
            "If an account exists for this email, a reset code was sent.";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/verify-admin-code")
    public ResponseEntity<AuthResponse> verifyAdminCode(@Valid @RequestBody AdminVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyAdminOtp(
                request.getAdminVerificationToken(), request.getCode()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String resetToken = authService.forgotPassword(request.getEmail(), passwordResetService);
        return ResponseEntity.ok(new ForgotPasswordResponse(FORGOT_PASSWORD_GENERIC, resetToken));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        passwordResetService.resetPassword(
                request.getResetToken(), request.getCode(), request.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Password reset successfully. Please log in with your new password."));
    }

    record ForgotPasswordResponse(String message, String resetToken) {}
}

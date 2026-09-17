package com.footballsim.controller;

import com.footballsim.service.FakeEmailService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Test-only endpoint to inspect emails sent by FakeEmailService.
 * Available ONLY when BOTH conditions are met:
 *   1. SPRING_PROFILES_ACTIVE=test
 *   2. app.otp.test-mode=true
 *
 * Impossible to register or access outside the test profile.
 */
@RestController
@RequestMapping("/api/test")
@Profile("test")
@ConditionalOnProperty(name = "app.otp.test-mode", havingValue = "true")
public class TestMailController {

    private final FakeEmailService fakeEmailService;

    public TestMailController(FakeEmailService fakeEmailService) {
        this.fakeEmailService = fakeEmailService;
    }

    @GetMapping("/mail")
    public ResponseEntity<List<FakeEmailService.SentEmail>> getSentEmails() {
        return ResponseEntity.ok(fakeEmailService.getSentEmails());
    }
}

package com.footballsim.service;

import com.footballsim.entity.User;
import com.footballsim.enums.Role;
import com.footballsim.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Creates the initial admin account at startup when bootstrap is enabled.
 * Public registration and login NEVER assign ADMIN role.
 * After the first successful bootstrap, set APP_ADMIN_BOOTSTRAP_ENABLED=false.
 */
@Service
public class AdminBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);
    private static final Pattern PASSWORD_POLICY =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    @Value("${app.admin.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.initial-password:}")
    private String adminInitialPassword;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled) {
            return;
        }

        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("Admin bootstrap is enabled but APP_ADMIN_EMAIL is not set. Skipping bootstrap.");
            return;
        }

        if (adminInitialPassword == null || adminInitialPassword.isBlank()) {
            log.warn("Admin bootstrap is enabled but APP_ADMIN_INITIAL_PASSWORD is not set. Skipping bootstrap.");
            return;
        }

        if (!PASSWORD_POLICY.matcher(adminInitialPassword).matches()) {
            log.warn("APP_ADMIN_INITIAL_PASSWORD does not meet the password policy (min 8 chars, uppercase, lowercase, digit). Skipping bootstrap.");
            return;
        }

        userRepository.findByEmail(adminEmail).ifPresentOrElse(existingUser -> {
            if (existingUser.getRole() == Role.ADMIN) {
                log.info("Admin account already exists for {}. Skipping bootstrap.", adminEmail);
            } else {
                log.warn("A USER account already exists for configured admin email {}. " +
                        "Manual intervention is required to promote this account to ADMIN. " +
                        "Bootstrap skipped to prevent silent privilege escalation.", adminEmail);
            }
        }, () -> {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail(adminEmail);
            admin.setPasswordHash(passwordEncoder.encode(adminInitialPassword));
            admin.setBalance(new BigDecimal("1000.00"));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
            log.info("Admin account bootstrapped for {}.", adminEmail);
        });
    }
}

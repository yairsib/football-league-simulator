package com.footballsim.controller;

import com.footballsim.entity.Round;
import com.footballsim.enums.RoundStatus;
import com.footballsim.repository.RoundRepository;
import com.footballsim.service.SimulationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Test-only endpoint to fast-forward the entire remaining season in one call.
 * Available ONLY when BOTH conditions are met:
 *   1. SPRING_PROFILES_ACTIVE=test
 *   2. app.otp.test-mode=true
 *
 * Used by the E2E season-completion spec to avoid simulating 26 rounds one by one.
 * Covered by TestSecurityConfig which permits all /api/test/** routes.
 */
@RestController
@RequestMapping("/api/test")
@Profile("test")
@ConditionalOnProperty(name = "app.otp.test-mode", havingValue = "true")
public class TestSimulationController {

    private final SimulationService simulationService;
    private final RoundRepository roundRepository;

    public TestSimulationController(SimulationService simulationService,
                                    RoundRepository roundRepository) {
        this.simulationService = simulationService;
        this.roundRepository = roundRepository;
    }

    @PostMapping("/simulate-all")
    public ResponseEntity<String> simulateAll() {
        List<Round> pending = roundRepository.findAll().stream()
                .filter(r -> r.getStatus() != RoundStatus.FINISHED)
                .sorted(Comparator.comparingInt(Round::getRoundNumber))
                .toList();

        for (Round round : pending) {
            if (round.getStatus() == RoundStatus.NOT_STARTED) {
                simulationService.openBettingForRound(round.getRoundNumber());
            }
            simulationService.simulateRound(round.getRoundNumber());
        }

        return ResponseEntity.ok("Simulated " + pending.size() + " remaining round(s)");
    }
}

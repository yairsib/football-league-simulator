package com.footballsim.controller;

import com.footballsim.dto.AdminOverviewResponse;
import com.footballsim.dto.DataImportResponse;
import com.footballsim.dto.DataValidationResponse;
import com.footballsim.dto.MatchResponse;
import com.footballsim.dto.RoundResponse;
import com.footballsim.dto.SeasonResetRequest;
import com.footballsim.dto.SeasonResetResponse;
import com.footballsim.service.AdminService;
import com.footballsim.service.LeagueDataImportService;
import com.footballsim.service.SeasonResetService;
import com.footballsim.service.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final SimulationService simulationService;
    private final LeagueDataImportService leagueDataImportService;
    private final SeasonResetService seasonResetService;

    public AdminController(AdminService adminService,
                           SimulationService simulationService,
                           LeagueDataImportService leagueDataImportService,
                           SeasonResetService seasonResetService) {
        this.adminService = adminService;
        this.simulationService = simulationService;
        this.leagueDataImportService = leagueDataImportService;
        this.seasonResetService = seasonResetService;
    }

    @GetMapping("/overview")
    public ResponseEntity<AdminOverviewResponse> getAdminOverview() {
        return ResponseEntity.ok(adminService.getAdminOverview());
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, String>> seedTeams() {
        return ResponseEntity.ok(Map.of("message", adminService.seedTeams()));
    }

    @GetMapping("/data/validate")
    public ResponseEntity<DataValidationResponse> validateLeagueData() {
        return ResponseEntity.ok(leagueDataImportService.validate());
    }

    @PostMapping("/data/import")
    public ResponseEntity<DataImportResponse> importLeagueData() {
        return ResponseEntity.ok(leagueDataImportService.importData());
    }

    @PostMapping("/generate-schedule")
    public ResponseEntity<Map<String, String>> generateSchedule() {
        return ResponseEntity.ok(Map.of("message", adminService.generateSchedule()));
    }

    @PostMapping("/rounds/{roundNumber}/open-betting")
    public ResponseEntity<List<MatchResponse>> openBettingForRound(@PathVariable int roundNumber) {
        return ResponseEntity.ok(simulationService.openBettingForRound(roundNumber));
    }

    @PostMapping("/matches/{matchId}/simulate")
    public ResponseEntity<MatchResponse> simulateMatch(@PathVariable Long matchId) {
        return ResponseEntity.ok(simulationService.simulateMatch(matchId));
    }

    @PostMapping("/rounds/{roundNumber}/simulate")
    public ResponseEntity<RoundResponse> simulateRound(@PathVariable int roundNumber) {
        return ResponseEntity.ok(simulationService.simulateRound(roundNumber));
    }

    @PostMapping("/season/reset")
    public ResponseEntity<SeasonResetResponse> resetSeason(@RequestBody SeasonResetRequest request) {
        return ResponseEntity.ok(seasonResetService.resetSeason(request));
    }
}

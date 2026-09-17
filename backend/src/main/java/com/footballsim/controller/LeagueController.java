package com.footballsim.controller;

import com.footballsim.dto.LeagueTableResponse;
import com.footballsim.service.LeagueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/league")
public class LeagueController {

    private final LeagueService leagueService;

    public LeagueController(LeagueService leagueService) {
        this.leagueService = leagueService;
    }

    @GetMapping("/table")
    public ResponseEntity<List<LeagueTableResponse>> getLeagueTable() {
        return ResponseEntity.ok(leagueService.getLeagueTable());
    }
}

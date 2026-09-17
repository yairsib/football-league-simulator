package com.footballsim.controller;

import com.footballsim.dto.TeamLineupResponse;
import com.footballsim.dto.TeamResponse;
import com.footballsim.dto.TeamSquadResponse;
import com.footballsim.service.PlayerService;
import com.footballsim.service.TeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;
    private final PlayerService playerService;

    public TeamController(TeamService teamService, PlayerService playerService) {
        this.teamService = teamService;
        this.playerService = playerService;
    }

    @GetMapping
    public ResponseEntity<List<TeamResponse>> getAllTeams() {
        return ResponseEntity.ok(teamService.getAllTeams());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamResponse> getTeamById(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.getTeamById(id));
    }

    @GetMapping("/{teamId}/squad")
    public ResponseEntity<TeamSquadResponse> getSquad(@PathVariable Long teamId) {
        return ResponseEntity.ok(playerService.getSquad(teamId));
    }

    @GetMapping("/{teamId}/lineup")
    public ResponseEntity<TeamLineupResponse> getLineup(@PathVariable Long teamId) {
        return ResponseEntity.ok(playerService.getLineup(teamId));
    }
}

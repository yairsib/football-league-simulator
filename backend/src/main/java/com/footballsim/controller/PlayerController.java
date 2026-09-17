package com.footballsim.controller;

import com.footballsim.dto.PlayerResponse;
import com.footballsim.dto.PlayerStatsResponse;
import com.footballsim.service.PlayerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping("/api/players/{playerId}")
    public ResponseEntity<PlayerResponse> getPlayer(@PathVariable Long playerId) {
        return ResponseEntity.ok(playerService.getPlayer(playerId));
    }

    @GetMapping("/api/teams/{teamId}/players/stats")
    public ResponseEntity<List<PlayerResponse>> getTeamPlayerStats(@PathVariable Long teamId) {
        return ResponseEntity.ok(playerService.getTeamPlayerStats(teamId));
    }

    @GetMapping("/api/players/leaders")
    public ResponseEntity<List<PlayerStatsResponse>> getLeaders(
            @RequestParam String stat,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(playerService.getLeaders(stat, limit));
    }
}

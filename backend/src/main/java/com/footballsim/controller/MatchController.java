package com.footballsim.controller;

import com.footballsim.dto.CorrectScoreOddsOption;
import com.footballsim.dto.HandicapOddsOption;
import com.footballsim.dto.MatchEventResponse;
import com.footballsim.dto.MatchLineupsResponse;
import com.footballsim.dto.MatchResponse;
import com.footballsim.service.MatchService;
import com.footballsim.service.PlayerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;
    private final PlayerService playerService;

    public MatchController(MatchService matchService, PlayerService playerService) {
        this.matchService = matchService;
        this.playerService = playerService;
    }

    @GetMapping
    public ResponseEntity<List<MatchResponse>> getAllMatches() {
        return ResponseEntity.ok(matchService.getAllMatches());
    }

    @GetMapping("/round/{roundNumber}")
    public ResponseEntity<List<MatchResponse>> getMatchesByRound(@PathVariable int roundNumber) {
        return ResponseEntity.ok(matchService.getMatchesByRound(roundNumber));
    }

    @GetMapping("/{matchId}/lineups")
    public ResponseEntity<MatchLineupsResponse> getMatchLineups(@PathVariable Long matchId) {
        return ResponseEntity.ok(playerService.getMatchLineups(matchId));
    }

    @GetMapping("/{matchId}/events")
    public ResponseEntity<List<MatchEventResponse>> getMatchEvents(@PathVariable Long matchId) {
        return ResponseEntity.ok(playerService.getMatchEvents(matchId));
    }

    @GetMapping("/{matchId}/correct-score-odds")
    public ResponseEntity<List<CorrectScoreOddsOption>> getCorrectScoreOdds(@PathVariable Long matchId) {
        return ResponseEntity.ok(matchService.getCorrectScoreOdds(matchId));
    }

    @GetMapping("/{matchId}/handicap-odds")
    public ResponseEntity<List<HandicapOddsOption>> getHandicapOdds(@PathVariable Long matchId) {
        return ResponseEntity.ok(matchService.getHandicapOddsOptions(matchId));
    }
}

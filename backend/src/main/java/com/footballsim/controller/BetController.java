package com.footballsim.controller;

import com.footballsim.dto.*;
import com.footballsim.service.BetService;
import com.footballsim.service.OddsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bets")
public class BetController {

    private final BetService betService;
    private final OddsService oddsService;

    public BetController(BetService betService, OddsService oddsService) {
        this.betService = betService;
        this.oddsService = oddsService;
    }

    @PostMapping
    public ResponseEntity<BetResponse> placeBet(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PlaceBetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(betService.placeBet(userDetails.getUsername(), request));
    }

    @PostMapping("/combo")
    public ResponseEntity<BetResponse> placeCombo(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ComboBetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(betService.placeCombo(userDetails.getUsername(), request));
    }

    @PostMapping("/correct-score")
    public ResponseEntity<BetResponse> placeCorrectScoreBet(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CorrectScoreBetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(betService.placeCorrectScoreBet(userDetails.getUsername(), request));
    }

    @PostMapping("/handicap")
    public ResponseEntity<BetResponse> placeHandicapBet(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody HandicapBetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(betService.placeHandicapBet(userDetails.getUsername(), request));
    }

    @PutMapping("/{betId}")
    public ResponseEntity<BetResponse> editBet(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long betId,
            @Valid @RequestBody UpdateBetRequest request) {
        return ResponseEntity.ok(betService.editBet(userDetails.getUsername(), betId, request));
    }

    @PutMapping("/{betId}/combo")
    public ResponseEntity<BetResponse> editCombo(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long betId,
            @Valid @RequestBody UpdateComboBetRequest request) {
        return ResponseEntity.ok(betService.editCombo(userDetails.getUsername(), betId, request));
    }

    @PutMapping("/{betId}/correct-score")
    public ResponseEntity<BetResponse> editCorrectScoreBet(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long betId,
            @Valid @RequestBody UpdateCorrectScoreBetRequest request) {
        return ResponseEntity.ok(betService.editCorrectScoreBet(userDetails.getUsername(), betId, request));
    }

    @PutMapping("/{betId}/handicap")
    public ResponseEntity<BetResponse> editHandicapBet(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long betId,
            @Valid @RequestBody UpdateHandicapBetRequest request) {
        return ResponseEntity.ok(betService.editHandicapBet(userDetails.getUsername(), betId, request));
    }

    @PostMapping("/{betId}/cancel")
    public ResponseEntity<BetResponse> cancelBet(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long betId) {
        return ResponseEntity.ok(betService.cancelBet(userDetails.getUsername(), betId));
    }

    // ─── Season bets ─────────────────────────────────────────────────────────

    @GetMapping("/season/status")
    public ResponseEntity<SeasonBetStatusResponse> getSeasonBetStatus() {
        return ResponseEntity.ok(betService.getSeasonBetStatus());
    }

    @GetMapping("/season/champion/odds")
    public ResponseEntity<List<ChampionOddsOption>> getChampionOdds() {
        return ResponseEntity.ok(oddsService.getChampionOdds());
    }

    @GetMapping("/season/top-scorer/odds")
    public ResponseEntity<List<TopScorerOddsOption>> getTopScorerOdds(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(oddsService.getTopScorerOdds(limit));
    }

    @PostMapping("/season/champion")
    public ResponseEntity<BetResponse> placeChampionBet(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChampionBetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(betService.placeChampionBet(userDetails.getUsername(), request));
    }

    @PostMapping("/season/top-scorer")
    public ResponseEntity<BetResponse> placeTopScorerBet(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TopScorerBetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(betService.placeTopScorerBet(userDetails.getUsername(), request));
    }

    @PutMapping("/{betId}/season/champion")
    public ResponseEntity<BetResponse> editChampionBet(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long betId,
            @Valid @RequestBody UpdateChampionBetRequest request) {
        return ResponseEntity.ok(betService.editChampionBet(userDetails.getUsername(), betId, request));
    }

    @PutMapping("/{betId}/season/top-scorer")
    public ResponseEntity<BetResponse> editTopScorerBet(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long betId,
            @Valid @RequestBody UpdateTopScorerBetRequest request) {
        return ResponseEntity.ok(betService.editTopScorerBet(userDetails.getUsername(), betId, request));
    }

    // ─── My bets ─────────────────────────────────────────────────────────────

    @GetMapping("/my")
    public ResponseEntity<List<BetResponse>> getMyBets(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(betService.getMyBets(userDetails.getUsername()));
    }

    @GetMapping("/my/open")
    public ResponseEntity<List<BetResponse>> getMyOpenBets(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(betService.getMyOpenBets(userDetails.getUsername()));
    }

    @GetMapping("/my/history")
    public ResponseEntity<List<BetResponse>> getMyBetHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(betService.getMyBetHistory(userDetails.getUsername()));
    }
}

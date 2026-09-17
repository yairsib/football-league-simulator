package com.footballsim.service;

import com.footballsim.dto.CorrectScoreOddsOption;
import com.footballsim.dto.HandicapOddsOption;
import com.footballsim.dto.MatchResponse;
import com.footballsim.entity.Match;
import com.footballsim.enums.HandicapSelection;
import com.footballsim.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final OddsService oddsService;

    public MatchService(MatchRepository matchRepository, OddsService oddsService) {
        this.matchRepository = matchRepository;
        this.oddsService = oddsService;
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> getAllMatches() {
        return matchRepository.findAll().stream()
                .map(MatchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> getMatchesByRound(int roundNumber) {
        return matchRepository.findByRound_RoundNumberOrderByIdAsc(roundNumber).stream()
                .map(MatchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CorrectScoreOddsOption> getCorrectScoreOdds(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new NoSuchElementException("Match not found"));
        return oddsService.getPopularCorrectScores(match);
    }

    /**
     * The handicap market as frozen on the match when betting opened. Repeated calls return the
     * identical prices, and BetService books handicap bets at exactly these values.
     */
    @Transactional(readOnly = true)
    public List<HandicapOddsOption> getHandicapOddsOptions(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new NoSuchElementException("Match not found"));
        if (match.getHandicapHomeMinusOneOdds() == null || match.getHandicapDrawOdds() == null
                || match.getHandicapAwayPlusOneOdds() == null) {
            throw new IllegalStateException("Handicap odds are available once betting opens for this match");
        }
        return List.of(
            new HandicapOddsOption(HandicapSelection.HOME_MINUS_ONE, "Home -1",       match.getHandicapHomeMinusOneOdds()),
            new HandicapOddsOption(HandicapSelection.HANDICAP_DRAW,  "Handicap Draw", match.getHandicapDrawOdds()),
            new HandicapOddsOption(HandicapSelection.AWAY_PLUS_ONE,  "Away +1",       match.getHandicapAwayPlusOneOdds())
        );
    }
}

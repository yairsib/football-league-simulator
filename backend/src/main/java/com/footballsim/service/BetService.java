package com.footballsim.service;

import com.footballsim.dto.*;
import com.footballsim.entity.Bet;
import com.footballsim.entity.BetSelection;
import com.footballsim.entity.Match;
import com.footballsim.entity.Player;
import com.footballsim.entity.Round;
import com.footballsim.entity.Team;
import com.footballsim.entity.User;
import com.footballsim.enums.BetMarket;
import com.footballsim.enums.BetStatus;
import com.footballsim.enums.BetType;
import com.footballsim.enums.HandicapSelection;
import com.footballsim.enums.MatchStatus;
import com.footballsim.enums.Prediction;
import com.footballsim.enums.RoundStatus;
import com.footballsim.repository.BetRepository;
import com.footballsim.repository.BetSelectionRepository;
import com.footballsim.repository.MatchRepository;
import com.footballsim.repository.PlayerRepository;
import com.footballsim.repository.RoundRepository;
import com.footballsim.repository.TeamRepository;
import com.footballsim.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BetService {

    private final BetRepository betRepository;
    private final BetSelectionRepository betSelectionRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final RoundRepository roundRepository;
    private final OddsService oddsService;

    public BetService(BetRepository betRepository,
                      BetSelectionRepository betSelectionRepository,
                      MatchRepository matchRepository,
                      UserRepository userRepository,
                      PlayerRepository playerRepository,
                      TeamRepository teamRepository,
                      RoundRepository roundRepository,
                      OddsService oddsService) {
        this.betRepository = betRepository;
        this.betSelectionRepository = betSelectionRepository;
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.roundRepository = roundRepository;
        this.oddsService = oddsService;
    }

    @Transactional
    public BetResponse placeBet(String userEmail, PlaceBetRequest request) {
        User user = requireUser(userEmail);

        Match match = matchRepository.findById(request.getMatchId())
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        if (match.getStatus() != MatchStatus.BETTING_OPEN) {
            throw new IllegalStateException("Betting is not open for this match");
        }

        if (userHasOpenBetOnMatch(user.getId(), match.getId(), null)) {
            throw new IllegalStateException("You have already placed a bet on this match");
        }

        BigDecimal amount = request.getAmount();

        if (amount.compareTo(user.getBalance()) > 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        BigDecimal odds = resolveOdds(match, request.getPrediction());
        BigDecimal possibleWin = amount.multiply(odds).setScale(2, RoundingMode.HALF_UP);

        user.setBalance(user.getBalance().subtract(amount));

        Bet bet = new Bet();
        bet.setUser(user);
        bet.setBetType(BetType.SINGLE);
        bet.setMarket(BetMarket.MATCH_RESULT);
        bet.setMatch(match);
        bet.setPrediction(request.getPrediction());
        bet.setAmount(amount);
        bet.setOdds(odds);
        bet.setPossibleWin(possibleWin);
        bet.setStatus(BetStatus.OPEN);

        return BetResponse.from(betRepository.save(bet));
    }

    @Transactional
    public BetResponse placeCorrectScoreBet(String userEmail, CorrectScoreBetRequest request) {
        User user = requireUser(userEmail);

        Match match = matchRepository.findById(request.getMatchId())
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        if (match.getStatus() != MatchStatus.BETTING_OPEN) {
            throw new IllegalStateException("Betting is not open for this match");
        }

        if (userHasOpenBetOnMatch(user.getId(), match.getId(), null)) {
            throw new IllegalStateException("You have already placed a bet on this match");
        }

        BigDecimal amount = request.getAmount();

        if (amount.compareTo(user.getBalance()) > 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        BigDecimal odds = oddsService.computeCorrectScoreOdds(match, request.getHomeGoals(), request.getAwayGoals());
        BigDecimal possibleWin = amount.multiply(odds).setScale(2, RoundingMode.HALF_UP);

        user.setBalance(user.getBalance().subtract(amount));

        Bet bet = new Bet();
        bet.setUser(user);
        bet.setBetType(BetType.SINGLE);
        bet.setMarket(BetMarket.CORRECT_SCORE);
        bet.setMatch(match);
        bet.setPrediction(null);
        bet.setPredictedHomeGoals(request.getHomeGoals());
        bet.setPredictedAwayGoals(request.getAwayGoals());
        bet.setAmount(amount);
        bet.setOdds(odds);
        bet.setPossibleWin(possibleWin);
        bet.setStatus(BetStatus.OPEN);

        return BetResponse.from(betRepository.save(bet));
    }

    @Transactional
    public BetResponse placeHandicapBet(String userEmail, HandicapBetRequest request) {
        User user = requireUser(userEmail);

        Match match = matchRepository.findById(request.getMatchId())
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        if (match.getStatus() != MatchStatus.BETTING_OPEN) {
            throw new IllegalStateException("Betting is not open for this match");
        }

        if (userHasOpenBetOnMatch(user.getId(), match.getId(), null)) {
            throw new IllegalStateException("You have already placed a bet on this match");
        }

        BigDecimal amount = request.getAmount();

        if (amount.compareTo(user.getBalance()) > 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        HandicapSelection selection = request.getSelection();
        BigDecimal odds = frozenHandicapOdds(match, selection);
        BigDecimal possibleWin = amount.multiply(odds).setScale(2, RoundingMode.HALF_UP);

        user.setBalance(user.getBalance().subtract(amount));

        Bet bet = new Bet();
        bet.setUser(user);
        bet.setBetType(BetType.SINGLE);
        bet.setMarket(BetMarket.HANDICAP);
        bet.setMatch(match);
        bet.setPrediction(null);
        bet.setHandicapSelection(selection);
        bet.setAmount(amount);
        bet.setOdds(odds);
        bet.setPossibleWin(possibleWin);
        bet.setStatus(BetStatus.OPEN);

        return BetResponse.from(betRepository.save(bet));
    }

    @Transactional
    public BetResponse placeCombo(String userEmail, ComboBetRequest request) {
        User user = requireUser(userEmail);

        List<BetSelectionRequest> selectionRequests = request.getSelections();
        if (selectionRequests.size() < 2) {
            throw new IllegalArgumentException("A combo bet must have at least 2 selections");
        }
        requireNoDuplicateMatches(selectionRequests);

        BigDecimal amount = request.getAmount();
        if (amount.compareTo(user.getBalance()) > 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        List<BetSelection> selections = new ArrayList<>();
        BigDecimal totalOdds = BigDecimal.ONE;
        for (BetSelectionRequest sr : selectionRequests) {
            Match match = matchRepository.findById(sr.getMatchId())
                    .orElseThrow(() -> new IllegalArgumentException("Match not found: " + sr.getMatchId()));

            if (match.getStatus() != MatchStatus.BETTING_OPEN) {
                throw new IllegalStateException("Betting is not open for match " + match.getId());
            }
            if (userHasOpenBetOnMatch(user.getId(), match.getId(), null)) {
                throw new IllegalStateException("You already have an open bet on match " + match.getId());
            }

            BigDecimal odds = resolveOdds(match, sr.getPrediction());
            totalOdds = totalOdds.multiply(odds);

            BetSelection selection = new BetSelection();
            selection.setMatch(match);
            selection.setPrediction(sr.getPrediction());
            selection.setOddsSnapshot(odds);
            selections.add(selection);
        }
        totalOdds = totalOdds.setScale(2, RoundingMode.HALF_UP);
        BigDecimal possibleWin = amount.multiply(totalOdds).setScale(2, RoundingMode.HALF_UP);

        user.setBalance(user.getBalance().subtract(amount));

        Bet bet = new Bet();
        bet.setUser(user);
        bet.setBetType(BetType.COMBO);
        bet.setAmount(amount);
        bet.setTotalOdds(totalOdds);
        bet.setPossibleWin(possibleWin);
        bet.setStatus(BetStatus.OPEN);
        for (BetSelection selection : selections) {
            selection.setBet(bet);
        }
        bet.setSelections(selections);

        return BetResponse.from(betRepository.save(bet));
    }

    @Transactional
    public BetResponse editBet(String userEmail, Long betId, UpdateBetRequest request) {
        User user = requireUser(userEmail);
        Bet bet = requireOwnedOpenBet(user, betId, BetType.SINGLE);
        if (bet.getMarket() != BetMarket.MATCH_RESULT) {
            throw new IllegalArgumentException("Bet " + betId + " is not a match-result bet");
        }

        Match match = bet.getMatch();
        if (match.getStatus() != MatchStatus.BETTING_OPEN) {
            throw new IllegalStateException("Cannot edit: betting is closed for this match");
        }

        BigDecimal newAmount = request.getAmount();
        applyAmountDelta(user, bet.getAmount(), newAmount);

        BigDecimal odds = bet.getOdds();
        Prediction newPrediction = request.getPrediction();
        if (newPrediction != bet.getPrediction()) {
            odds = resolveOdds(match, newPrediction);
            bet.setPrediction(newPrediction);
            bet.setOdds(odds);
        }

        bet.setAmount(newAmount);
        bet.setPossibleWin(newAmount.multiply(odds).setScale(2, RoundingMode.HALF_UP));

        return BetResponse.from(bet);
    }

    @Transactional
    public BetResponse editCorrectScoreBet(String userEmail, Long betId, UpdateCorrectScoreBetRequest request) {
        User user = requireUser(userEmail);
        Bet bet = requireOwnedOpenBet(user, betId, BetType.SINGLE);
        if (bet.getMarket() != BetMarket.CORRECT_SCORE) {
            throw new IllegalArgumentException("Bet " + betId + " is not a correct-score bet");
        }

        Match match = bet.getMatch();
        if (match.getStatus() != MatchStatus.BETTING_OPEN) {
            throw new IllegalStateException("Cannot edit: betting is closed for this match");
        }

        BigDecimal newAmount = request.getAmount();
        applyAmountDelta(user, bet.getAmount(), newAmount);

        BigDecimal odds = bet.getOdds();
        Integer newHomeGoals = request.getHomeGoals();
        Integer newAwayGoals = request.getAwayGoals();
        if (!newHomeGoals.equals(bet.getPredictedHomeGoals()) || !newAwayGoals.equals(bet.getPredictedAwayGoals())) {
            odds = oddsService.computeCorrectScoreOdds(match, newHomeGoals, newAwayGoals);
            bet.setPredictedHomeGoals(newHomeGoals);
            bet.setPredictedAwayGoals(newAwayGoals);
            bet.setOdds(odds);
        }

        bet.setAmount(newAmount);
        bet.setPossibleWin(newAmount.multiply(odds).setScale(2, RoundingMode.HALF_UP));

        return BetResponse.from(bet);
    }

    @Transactional
    public BetResponse editHandicapBet(String userEmail, Long betId, UpdateHandicapBetRequest request) {
        User user = requireUser(userEmail);
        Bet bet = requireOwnedOpenBet(user, betId, BetType.SINGLE);
        if (bet.getMarket() != BetMarket.HANDICAP) {
            throw new IllegalArgumentException("Bet " + betId + " is not a handicap bet");
        }

        Match match = bet.getMatch();
        if (match.getStatus() != MatchStatus.BETTING_OPEN) {
            throw new IllegalStateException("Cannot edit: betting is closed for this match");
        }

        BigDecimal newAmount = request.getAmount();
        applyAmountDelta(user, bet.getAmount(), newAmount);

        HandicapSelection newSelection = request.getSelection();
        BigDecimal odds = bet.getOdds();
        if (newSelection != bet.getHandicapSelection()) {
            odds = frozenHandicapOdds(match, newSelection);
            bet.setHandicapSelection(newSelection);
            bet.setOdds(odds);
        }

        bet.setAmount(newAmount);
        bet.setPossibleWin(newAmount.multiply(odds).setScale(2, RoundingMode.HALF_UP));

        return BetResponse.from(bet);
    }

    @Transactional
    public BetResponse editCombo(String userEmail, Long betId, UpdateComboBetRequest request) {
        User user = requireUser(userEmail);
        Bet bet = requireOwnedOpenBet(user, betId, BetType.COMBO);

        for (BetSelection existing : bet.getSelections()) {
            if (existing.getMatch().getStatus() != MatchStatus.BETTING_OPEN) {
                throw new IllegalStateException("Cannot edit: betting is closed for match " + existing.getMatch().getId());
            }
        }

        List<BetSelectionRequest> selectionRequests = request.getSelections();
        if (selectionRequests.size() < 2) {
            throw new IllegalArgumentException("A combo bet must have at least 2 selections");
        }
        requireNoDuplicateMatches(selectionRequests);

        List<BetSelection> newSelections = new ArrayList<>();
        BigDecimal totalOdds = BigDecimal.ONE;
        for (BetSelectionRequest sr : selectionRequests) {
            Match match = matchRepository.findById(sr.getMatchId())
                    .orElseThrow(() -> new IllegalArgumentException("Match not found: " + sr.getMatchId()));

            if (match.getStatus() != MatchStatus.BETTING_OPEN) {
                throw new IllegalStateException("Betting is not open for match " + match.getId());
            }
            if (userHasOpenBetOnMatch(user.getId(), match.getId(), bet.getId())) {
                throw new IllegalStateException("You already have an open bet on match " + match.getId());
            }

            BigDecimal odds = resolveOdds(match, sr.getPrediction());
            totalOdds = totalOdds.multiply(odds);

            BetSelection selection = new BetSelection();
            selection.setBet(bet);
            selection.setMatch(match);
            selection.setPrediction(sr.getPrediction());
            selection.setOddsSnapshot(odds);
            newSelections.add(selection);
        }
        totalOdds = totalOdds.setScale(2, RoundingMode.HALF_UP);

        BigDecimal newAmount = request.getAmount();
        applyAmountDelta(user, bet.getAmount(), newAmount);

        bet.getSelections().clear();
        bet.getSelections().addAll(newSelections);
        bet.setAmount(newAmount);
        bet.setTotalOdds(totalOdds);
        bet.setPossibleWin(newAmount.multiply(totalOdds).setScale(2, RoundingMode.HALF_UP));

        return BetResponse.from(bet);
    }

    @Transactional
    public BetResponse cancelBet(String userEmail, Long betId) {
        User user = requireUser(userEmail);
        Bet bet = betRepository.findById(betId)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new NoSuchElementException("Bet not found"));

        if (bet.getStatus() != BetStatus.OPEN) {
            throw new IllegalStateException("Only open bets can be cancelled");
        }

        // Season bets check the season lock; match bets check match status
        if (bet.getMarket() == BetMarket.CHAMPION || bet.getMarket() == BetMarket.TOP_SCORER) {
            if (!isSeasonBettingOpen()) {
                throw new IllegalStateException("Cannot cancel: season betting is locked because Round 1 has started");
            }
        } else {
            for (Match match : involvedMatches(bet)) {
                if (match.getStatus() != MatchStatus.BETTING_OPEN) {
                    throw new IllegalStateException("Cannot cancel: match " + match.getId() + " is no longer open for betting");
                }
            }
        }

        bet.setStatus(BetStatus.CANCELLED);
        bet.setSettledAt(LocalDateTime.now());
        user.setBalance(user.getBalance().add(bet.getAmount()));

        return BetResponse.from(bet);
    }

    @Transactional
    public void settleBetsForMatch(Match match) {
        Prediction outcome = determineOutcome(match);

        List<Bet> openSingleBets = betRepository.findByMatch_IdAndStatus(match.getId(), BetStatus.OPEN);
        for (Bet bet : openSingleBets) {
            User betUser = bet.getUser();
            boolean won;
            if (bet.getMarket() == BetMarket.CORRECT_SCORE) {
                won = match.getHomeGoals().equals(bet.getPredictedHomeGoals())
                        && match.getAwayGoals().equals(bet.getPredictedAwayGoals());
            } else if (bet.getMarket() == BetMarket.HANDICAP) {
                int diff = match.getHomeGoals() - 1 - match.getAwayGoals();
                won = switch (bet.getHandicapSelection()) {
                    case HOME_MINUS_ONE -> diff > 0;
                    case HANDICAP_DRAW  -> diff == 0;
                    case AWAY_PLUS_ONE  -> diff < 0;
                };
            } else {
                won = bet.getPrediction() == outcome;
            }
            if (won) {
                bet.setStatus(BetStatus.WON);
                bet.setProfit(bet.getPossibleWin().subtract(bet.getAmount()));
                betUser.setBalance(betUser.getBalance().add(bet.getPossibleWin()));
            } else {
                bet.setStatus(BetStatus.LOST);
                bet.setProfit(bet.getAmount().negate());
            }
            bet.setSettledAt(LocalDateTime.now());
        }

        List<BetSelection> openComboSelections = betSelectionRepository.findByMatch_IdAndBet_Status(match.getId(), BetStatus.OPEN);
        Set<Bet> combosToEvaluate = new LinkedHashSet<>();
        for (BetSelection selection : openComboSelections) {
            combosToEvaluate.add(selection.getBet());
        }
        for (Bet combo : combosToEvaluate) {
            settleComboIfDecided(combo);
        }
    }

    @Transactional(readOnly = true)
    public List<BetResponse> getMyBets(String userEmail) {
        User user = requireUser(userEmail);
        return betRepository.findByUser_IdOrderByCreatedAtDesc(user.getId())
                .stream().map(BetResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BetResponse> getMyOpenBets(String userEmail) {
        User user = requireUser(userEmail);
        return betRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(user.getId(), BetStatus.OPEN)
                .stream().map(BetResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BetResponse> getMyBetHistory(String userEmail) {
        User user = requireUser(userEmail);
        return betRepository.findByUser_IdAndStatusNotOrderByCreatedAtDesc(user.getId(), BetStatus.OPEN)
                .stream().map(BetResponse::from).toList();
    }

    /**
     * Re-evaluates an OPEN combo after one of its selections' matches has finished.
     * Idempotent: only acts on bets still OPEN, so repeated settlement calls never double-pay/refund.
     * - Any finished selection whose prediction didn't match the result -> combo LOST.
     * - Every selection finished and won -> combo WON (credit possibleWin).
     * - Otherwise -> remains OPEN (some selections still pending, none lost yet).
     */
    private void settleComboIfDecided(Bet combo) {
        if (combo.getStatus() != BetStatus.OPEN) {
            return;
        }

        boolean anyLost = false;
        boolean allFinished = true;
        for (BetSelection selection : combo.getSelections()) {
            Match selectionMatch = selection.getMatch();
            if (selectionMatch.getStatus() == MatchStatus.FINISHED) {
                if (selection.getPrediction() != determineOutcome(selectionMatch)) {
                    anyLost = true;
                    break;
                }
            } else {
                allFinished = false;
            }
        }

        if (anyLost) {
            combo.setStatus(BetStatus.LOST);
            combo.setProfit(combo.getAmount().negate());
            combo.setSettledAt(LocalDateTime.now());
        } else if (allFinished) {
            combo.setStatus(BetStatus.WON);
            combo.setProfit(combo.getPossibleWin().subtract(combo.getAmount()));
            combo.getUser().setBalance(combo.getUser().getBalance().add(combo.getPossibleWin()));
            combo.setSettledAt(LocalDateTime.now());
        }
        // else: still pending selections and nothing lost yet -> remains OPEN
    }

    private void applyAmountDelta(User user, BigDecimal oldAmount, BigDecimal newAmount) {
        BigDecimal delta = newAmount.subtract(oldAmount);
        int cmp = delta.signum();
        if (cmp > 0) {
            if (delta.compareTo(user.getBalance()) > 0) {
                throw new IllegalArgumentException("Insufficient balance");
            }
            user.setBalance(user.getBalance().subtract(delta));
        } else if (cmp < 0) {
            user.setBalance(user.getBalance().add(delta.negate()));
        }
    }

    /**
     * The handicap price frozen on the match when betting opened — exactly what the market
     * displayed. Never recomputed here, so display and booking can never differ.
     */
    static BigDecimal frozenHandicapOdds(Match match, HandicapSelection selection) {
        BigDecimal odds = switch (selection) {
            case HOME_MINUS_ONE -> match.getHandicapHomeMinusOneOdds();
            case HANDICAP_DRAW  -> match.getHandicapDrawOdds();
            case AWAY_PLUS_ONE  -> match.getHandicapAwayPlusOneOdds();
        };
        if (odds == null) {
            throw new IllegalStateException("Handicap odds are not available for this match yet");
        }
        return odds;
    }

    private Bet requireOwnedOpenBet(User user, Long betId, BetType expectedType) {
        Bet bet = betRepository.findById(betId)
                .filter(b -> b.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new NoSuchElementException("Bet not found"));
        if (bet.getBetType() != expectedType) {
            throw new IllegalArgumentException("Bet " + betId + " is not a " + expectedType.name().toLowerCase() + " bet");
        }
        if (bet.getStatus() != BetStatus.OPEN) {
            throw new IllegalStateException("Only open bets can be edited");
        }
        return bet;
    }

    private List<Match> involvedMatches(Bet bet) {
        // Season bets (CHAMPION/TOP_SCORER) have no match; handled separately in cancelBet/editBet
        if (bet.getMarket() == BetMarket.CHAMPION || bet.getMarket() == BetMarket.TOP_SCORER) {
            return List.of();
        }
        if (bet.getBetType() == BetType.SINGLE) {
            return List.of(bet.getMatch());
        }
        return bet.getSelections().stream().map(BetSelection::getMatch).toList();
    }

    /** True if the user has any other OPEN bet (single or combo selection) involving this match. */
    private boolean userHasOpenBetOnMatch(Long userId, Long matchId, Long excludeBetId) {
        return betRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(userId, BetStatus.OPEN).stream()
                .filter(b -> excludeBetId == null || !b.getId().equals(excludeBetId))
                .anyMatch(b -> involvesMatch(b, matchId));
    }

    private boolean involvesMatch(Bet bet, Long matchId) {
        // Season bets are never associated with a specific match
        if (bet.getMarket() == BetMarket.CHAMPION || bet.getMarket() == BetMarket.TOP_SCORER) {
            return false;
        }
        if (bet.getBetType() == BetType.SINGLE) {
            return bet.getMatch() != null && bet.getMatch().getId().equals(matchId);
        }
        return bet.getSelections().stream().anyMatch(s -> s.getMatch().getId().equals(matchId));
    }

    /** True if user already has an OPEN CHAMPION or TOP_SCORER bet of the given market. */
    private boolean userHasOpenSeasonBet(Long userId, BetMarket market, Long excludeBetId) {
        return betRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(userId, BetStatus.OPEN).stream()
                .filter(b -> excludeBetId == null || !b.getId().equals(excludeBetId))
                .anyMatch(b -> b.getMarket() == market);
    }

    private void requireNoDuplicateMatches(List<BetSelectionRequest> selections) {
        Set<Long> matchIds = new HashSet<>();
        for (BetSelectionRequest sr : selections) {
            if (!matchIds.add(sr.getMatchId())) {
                throw new IllegalArgumentException("A combo cannot contain the same match twice");
            }
        }
    }

    private User requireUser(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private BigDecimal resolveOdds(Match match, Prediction prediction) {
        return switch (prediction) {
            case HOME_WIN -> match.getHomeOdds();
            case DRAW -> match.getDrawOdds();
            case AWAY_WIN -> match.getAwayOdds();
        };
    }

    private Prediction determineOutcome(Match match) {
        int hg = match.getHomeGoals();
        int ag = match.getAwayGoals();
        if (hg > ag) return Prediction.HOME_WIN;
        else if (hg == ag) return Prediction.DRAW;
        else return Prediction.AWAY_WIN;
    }

    // ─── Season bets ─────────────────────────────────────────────────────────

    /**
     * Returns whether season betting (CHAMPION / TOP_SCORER) is currently open.
     * Open only when: schedule exists, Round 1 exists, and no Round 1 match is FINISHED or IN_PROGRESS.
     */
    @Transactional(readOnly = true)
    public SeasonBetStatusResponse getSeasonBetStatus() {
        if (isSeasonBettingOpen()) {
            return new SeasonBetStatusResponse(true, "Season bets are open before Round 1 is played");
        }
        Round round1 = roundRepository.findByRoundNumber(1).orElse(null);
        if (round1 == null) {
            return new SeasonBetStatusResponse(false, "No schedule generated yet");
        }
        List<Round> allRounds = roundRepository.findAll();
        boolean allFinished = !allRounds.isEmpty()
                && allRounds.stream().allMatch(r -> r.getStatus() == RoundStatus.FINISHED);
        if (allFinished) {
            return new SeasonBetStatusResponse(false, "Season is complete — final standings are in");
        }
        return new SeasonBetStatusResponse(false, "Season bets are locked because Round 1 has started");
    }

    public boolean isSeasonBettingOpen() {
        Round round1 = roundRepository.findByRoundNumber(1).orElse(null);
        if (round1 == null) {
            return false;
        }
        if (round1.getStatus() == RoundStatus.FINISHED) {
            return false;
        }
        List<Match> round1Matches = matchRepository.findByRound_RoundNumberOrderByIdAsc(1);
        if (round1Matches.isEmpty()) {
            return false;
        }
        return round1Matches.stream().noneMatch(m ->
                m.getStatus() == MatchStatus.FINISHED || m.getStatus() == MatchStatus.IN_PROGRESS);
    }

    @Transactional
    public BetResponse placeChampionBet(String userEmail, ChampionBetRequest request) {
        User user = requireUser(userEmail);

        if (!isSeasonBettingOpen()) {
            throw new IllegalStateException("Season betting is locked because Round 1 has started");
        }
        if (userHasOpenSeasonBet(user.getId(), BetMarket.CHAMPION, null)) {
            throw new IllegalStateException("You already have an open Champion bet. Edit or cancel it before placing a new one.");
        }

        Team team = teamRepository.findById(request.getTeamId())
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        BigDecimal amount = request.getAmount();
        if (amount.compareTo(user.getBalance()) > 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        BigDecimal odds = oddsService.getChampionOddsForTeam(team);
        BigDecimal possibleWin = amount.multiply(odds).setScale(2, RoundingMode.HALF_UP);

        user.setBalance(user.getBalance().subtract(amount));

        Bet bet = new Bet();
        bet.setUser(user);
        bet.setBetType(BetType.SINGLE);
        bet.setMarket(BetMarket.CHAMPION);
        bet.setSelectedTeam(team);
        bet.setAmount(amount);
        bet.setOdds(odds);
        bet.setPossibleWin(possibleWin);
        bet.setStatus(BetStatus.OPEN);

        return BetResponse.from(betRepository.save(bet));
    }

    @Transactional
    public BetResponse placeTopScorerBet(String userEmail, TopScorerBetRequest request) {
        User user = requireUser(userEmail);

        if (!isSeasonBettingOpen()) {
            throw new IllegalStateException("Season betting is locked because Round 1 has started");
        }
        if (userHasOpenSeasonBet(user.getId(), BetMarket.TOP_SCORER, null)) {
            throw new IllegalStateException("You already have an open Top Scorer bet. Edit or cancel it before placing a new one.");
        }

        Player player = playerRepository.findById(request.getPlayerId())
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));

        if (!oddsService.isEligibleTopScorerCandidate(player)) {
            throw new IllegalArgumentException("Player is not an eligible top-scorer candidate (position must be ST/LW/RW/AM/CM and player must be available)");
        }

        BigDecimal amount = request.getAmount();
        if (amount.compareTo(user.getBalance()) > 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        BigDecimal odds = oddsService.getTopScorerOddsForPlayer(player);
        BigDecimal possibleWin = amount.multiply(odds).setScale(2, RoundingMode.HALF_UP);

        user.setBalance(user.getBalance().subtract(amount));

        Bet bet = new Bet();
        bet.setUser(user);
        bet.setBetType(BetType.SINGLE);
        bet.setMarket(BetMarket.TOP_SCORER);
        bet.setSelectedPlayer(player);
        bet.setAmount(amount);
        bet.setOdds(odds);
        bet.setPossibleWin(possibleWin);
        bet.setStatus(BetStatus.OPEN);

        return BetResponse.from(betRepository.save(bet));
    }

    @Transactional
    public BetResponse editChampionBet(String userEmail, Long betId, UpdateChampionBetRequest request) {
        User user = requireUser(userEmail);
        Bet bet = requireOwnedOpenBet(user, betId, BetType.SINGLE);
        if (bet.getMarket() != BetMarket.CHAMPION) {
            throw new IllegalArgumentException("Bet " + betId + " is not a champion bet");
        }
        if (!isSeasonBettingOpen()) {
            throw new IllegalStateException("Cannot edit: season betting is locked because Round 1 has started");
        }

        Team newTeam = teamRepository.findById(request.getTeamId())
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        BigDecimal newAmount = request.getAmount();
        applyAmountDelta(user, bet.getAmount(), newAmount);

        BigDecimal odds = bet.getOdds();
        if (!newTeam.getId().equals(bet.getSelectedTeam().getId())) {
            odds = oddsService.getChampionOddsForTeam(newTeam);
            bet.setSelectedTeam(newTeam);
            bet.setOdds(odds);
        }

        bet.setAmount(newAmount);
        bet.setPossibleWin(newAmount.multiply(odds).setScale(2, RoundingMode.HALF_UP));

        return BetResponse.from(bet);
    }

    @Transactional
    public BetResponse editTopScorerBet(String userEmail, Long betId, UpdateTopScorerBetRequest request) {
        User user = requireUser(userEmail);
        Bet bet = requireOwnedOpenBet(user, betId, BetType.SINGLE);
        if (bet.getMarket() != BetMarket.TOP_SCORER) {
            throw new IllegalArgumentException("Bet " + betId + " is not a top-scorer bet");
        }
        if (!isSeasonBettingOpen()) {
            throw new IllegalStateException("Cannot edit: season betting is locked because Round 1 has started");
        }

        Player newPlayer = playerRepository.findById(request.getPlayerId())
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));

        if (!oddsService.isEligibleTopScorerCandidate(newPlayer)) {
            throw new IllegalArgumentException("Player is not an eligible top-scorer candidate");
        }

        BigDecimal newAmount = request.getAmount();
        applyAmountDelta(user, bet.getAmount(), newAmount);

        BigDecimal odds = bet.getOdds();
        if (!newPlayer.getId().equals(bet.getSelectedPlayer().getId())) {
            odds = oddsService.getTopScorerOddsForPlayer(newPlayer);
            bet.setSelectedPlayer(newPlayer);
            bet.setOdds(odds);
        }

        bet.setAmount(newAmount);
        bet.setPossibleWin(newAmount.multiply(odds).setScale(2, RoundingMode.HALF_UP));

        return BetResponse.from(bet);
    }

    /**
     * Settles all OPEN CHAMPION and TOP_SCORER bets once the entire season is complete.
     * Idempotent: only acts on OPEN bets. Called from SimulationService after each match settles.
     */
    @Transactional
    public void settleSeasonBets() {
        if (!isSeasonComplete()) {
            return;
        }

        List<Bet> openChampionBets = betRepository.findByMarketAndStatus(BetMarket.CHAMPION, BetStatus.OPEN);
        if (!openChampionBets.isEmpty()) {
            Team champion = determineChampion();
            for (Bet bet : openChampionBets) {
                boolean won = bet.getSelectedTeam().getId().equals(champion.getId());
                settleSeasonBet(bet, won);
            }
        }

        List<Bet> openTopScorerBets = betRepository.findByMarketAndStatus(BetMarket.TOP_SCORER, BetStatus.OPEN);
        if (!openTopScorerBets.isEmpty()) {
            Set<Long> topScorerIds = determineTopScorerIds();
            for (Bet bet : openTopScorerBets) {
                boolean won = topScorerIds.contains(bet.getSelectedPlayer().getId());
                settleSeasonBet(bet, won);
            }
        }
    }

    private void settleSeasonBet(Bet bet, boolean won) {
        if (won) {
            bet.setStatus(BetStatus.WON);
            bet.setProfit(bet.getPossibleWin().subtract(bet.getAmount()));
            bet.getUser().setBalance(bet.getUser().getBalance().add(bet.getPossibleWin()));
        } else {
            bet.setStatus(BetStatus.LOST);
            bet.setProfit(bet.getAmount().negate());
        }
        bet.setSettledAt(LocalDateTime.now());
    }

    private boolean isSeasonComplete() {
        List<Round> rounds = roundRepository.findAll();
        return !rounds.isEmpty() && rounds.stream().allMatch(r -> r.getStatus() == RoundStatus.FINISHED);
    }

    /** Determines the league champion using the same sort order as the league table (points → GD → GF → name asc). */
    private Team determineChampion() {
        return teamRepository.findAll().stream()
                .max(Comparator.comparingInt(Team::getPoints)
                        .thenComparingInt(t -> t.getGoalsFor() - t.getGoalsAgainst())
                        .thenComparingInt(Team::getGoalsFor)
                        .thenComparing(Comparator.comparing(Team::getName).reversed()))
                .orElseThrow(() -> new IllegalStateException("No teams found for champion determination"));
    }

    /** Returns IDs of all players tied at the maximum goals count. All tied players win TOP_SCORER bets. */
    private Set<Long> determineTopScorerIds() {
        List<Player> allPlayers = playerRepository.findAll();
        int maxGoals = allPlayers.stream().mapToInt(Player::getGoals).max().orElse(0);
        if (maxGoals == 0) {
            return Set.of();
        }
        return allPlayers.stream()
                .filter(p -> p.getGoals() == maxGoals)
                .map(Player::getId)
                .collect(Collectors.toSet());
    }
}

package com.footballsim.service;

import com.footballsim.dto.*;
import com.footballsim.entity.*;
import com.footballsim.enums.BetMarket;
import com.footballsim.enums.BetStatus;
import com.footballsim.enums.BetType;
import com.footballsim.enums.HandicapSelection;
import com.footballsim.enums.MatchStatus;
import com.footballsim.enums.Prediction;
import com.footballsim.repository.BetRepository;
import com.footballsim.repository.BetSelectionRepository;
import com.footballsim.repository.MatchRepository;
import com.footballsim.repository.PlayerRepository;
import com.footballsim.repository.RoundRepository;
import com.footballsim.repository.TeamRepository;
import com.footballsim.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BetServiceTest {

    @Mock private BetRepository betRepository;
    @Mock private BetSelectionRepository betSelectionRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private UserRepository userRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private RoundRepository roundRepository;

    // OddsService is a concrete class; rather than mock it (Mockito's inline mock maker can't
    // subclass classes compiled for newer JDKs in this environment), use a real instance backed
    // by a mocked PlayerRepository, exactly like OddsServiceTest does.
    private OddsService oddsService;
    private BetService betService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setBalance(new BigDecimal("1000.00"));

        lenient().when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        lenient().when(betRepository.save(any(Bet.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(any())).thenReturn(List.of());
        lenientNoOpenBets();

        oddsService = new OddsService(playerRepository, teamRepository);
        betService = new BetService(betRepository, betSelectionRepository, matchRepository, userRepository,
                playerRepository, teamRepository, roundRepository, oddsService);
    }

    private void lenientNoOpenBets() {
        lenient().when(betRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(anyLong(), eq(BetStatus.OPEN)))
                .thenReturn(List.of());
    }

    private Match openMatch(long id, String home, String away) {
        Team homeTeam = new Team();
        homeTeam.setName(home);
        Team awayTeam = new Team();
        awayTeam.setName(away);

        Match match = new Match();
        match.setId(id);
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setStatus(MatchStatus.BETTING_OPEN);
        match.setBettingOpen(true);
        match.setHomeOdds(new BigDecimal("2.00"));
        match.setDrawOdds(new BigDecimal("3.00"));
        match.setAwayOdds(new BigDecimal("4.00"));
        return match;
    }

    private Match finishedMatch(long id, int homeGoals, int awayGoals, BigDecimal homeOdds, BigDecimal drawOdds, BigDecimal awayOdds) {
        Match match = openMatch(id, "Home" + id, "Away" + id);
        match.setStatus(MatchStatus.FINISHED);
        match.setBettingOpen(false);
        match.setHomeGoals(homeGoals);
        match.setAwayGoals(awayGoals);
        match.setHomeOdds(homeOdds);
        match.setDrawOdds(drawOdds);
        match.setAwayOdds(awayOdds);
        return match;
    }

    // ─── Single bet regression ────────────────────────────────────────────

    @Test
    void placeBet_deductsBalanceAndSnapshotsOdds() {
        Match match = openMatch(10L, "Home", "Away");
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        PlaceBetRequest request = new PlaceBetRequest();
        request.setMatchId(10L);
        request.setPrediction(Prediction.HOME_WIN);
        request.setAmount(new BigDecimal("100.00"));

        BetResponse response = betService.placeBet("user@test.com", request);

        assertThat(user.getBalance()).isEqualByComparingTo("900.00");
        assertThat(response.getBetType()).isEqualTo(BetType.SINGLE);
        assertThat(response.getOdds()).isEqualByComparingTo("2.00");
        assertThat(response.getPossibleWin()).isEqualByComparingTo("200.00");
    }

    @Test
    void placeBet_setsMarketToMatchResult() {
        Match match = openMatch(10L, "Home", "Away");
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        PlaceBetRequest request = new PlaceBetRequest();
        request.setMatchId(10L);
        request.setPrediction(Prediction.HOME_WIN);
        request.setAmount(new BigDecimal("10.00"));

        BetResponse response = betService.placeBet("user@test.com", request);

        assertThat(response.getMarket()).isEqualTo(BetMarket.MATCH_RESULT);
        assertThat(response.getDisplayLabel()).isEqualTo("Match Result");
    }

    @Test
    void placeBet_rejectsWhenMatchNotBettingOpen() {
        Match match = openMatch(10L, "Home", "Away");
        match.setStatus(MatchStatus.SCHEDULED);
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        PlaceBetRequest request = new PlaceBetRequest();
        request.setMatchId(10L);
        request.setPrediction(Prediction.HOME_WIN);
        request.setAmount(new BigDecimal("50.00"));

        assertThatThrownBy(() -> betService.placeBet("user@test.com", request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void placeBet_rejectsDuplicateBetOnSameMatch() {
        Match match = openMatch(10L, "Home", "Away");
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        Bet existing = new Bet();
        existing.setBetType(BetType.SINGLE);
        existing.setMatch(match);
        when(betRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(1L, BetStatus.OPEN))
                .thenReturn(List.of(existing));

        PlaceBetRequest request = new PlaceBetRequest();
        request.setMatchId(10L);
        request.setPrediction(Prediction.AWAY_WIN);
        request.setAmount(new BigDecimal("50.00"));

        assertThatThrownBy(() -> betService.placeBet("user@test.com", request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void placeBet_rejectsWhenAmountExceedsBalance() {
        Match match = openMatch(10L, "Home", "Away");
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        PlaceBetRequest request = new PlaceBetRequest();
        request.setMatchId(10L);
        request.setPrediction(Prediction.HOME_WIN);
        request.setAmount(new BigDecimal("5000.00"));

        assertThatThrownBy(() -> betService.placeBet("user@test.com", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settleBetsForMatch_settlesSingleBetsAsWonAndLost() {
        Match match = finishedMatch(10L, 2, 0, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));

        User winnerUser = new User();
        winnerUser.setId(2L);
        winnerUser.setBalance(new BigDecimal("100.00"));
        Bet winningBet = new Bet();
        winningBet.setUser(winnerUser);
        winningBet.setBetType(BetType.SINGLE);
        winningBet.setMatch(match);
        winningBet.setPrediction(Prediction.HOME_WIN);
        winningBet.setAmount(new BigDecimal("10.00"));
        winningBet.setOdds(new BigDecimal("2.00"));
        winningBet.setPossibleWin(new BigDecimal("20.00"));
        winningBet.setStatus(BetStatus.OPEN);

        User loserUser = new User();
        loserUser.setId(3L);
        loserUser.setBalance(new BigDecimal("100.00"));
        Bet losingBet = new Bet();
        losingBet.setUser(loserUser);
        losingBet.setBetType(BetType.SINGLE);
        losingBet.setMatch(match);
        losingBet.setPrediction(Prediction.AWAY_WIN);
        losingBet.setAmount(new BigDecimal("10.00"));
        losingBet.setOdds(new BigDecimal("4.00"));
        losingBet.setPossibleWin(new BigDecimal("40.00"));
        losingBet.setStatus(BetStatus.OPEN);

        when(betRepository.findByMatch_IdAndStatus(10L, BetStatus.OPEN)).thenReturn(List.of(winningBet, losingBet));
        when(betSelectionRepository.findByMatch_IdAndBet_Status(10L, BetStatus.OPEN)).thenReturn(List.of());

        betService.settleBetsForMatch(match);

        assertThat(winningBet.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(winningBet.getProfit()).isEqualByComparingTo("10.00");
        assertThat(winnerUser.getBalance()).isEqualByComparingTo("120.00");

        assertThat(losingBet.getStatus()).isEqualTo(BetStatus.LOST);
        assertThat(losingBet.getProfit()).isEqualByComparingTo("-10.00");
        assertThat(loserUser.getBalance()).isEqualByComparingTo("100.00");
    }

    // ─── Combo bets ───────────────────────────────────────────────────────

    @Test
    void placeCombo_rejectsFewerThanTwoSelections() {
        ComboBetRequest request = new ComboBetRequest();
        request.setAmount(new BigDecimal("10.00"));
        BetSelectionRequest sel = new BetSelectionRequest();
        sel.setMatchId(10L);
        sel.setPrediction(Prediction.HOME_WIN);
        request.setSelections(List.of(sel));

        assertThatThrownBy(() -> betService.placeCombo("user@test.com", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void placeCombo_rejectsDuplicateMatches() {
        ComboBetRequest request = new ComboBetRequest();
        request.setAmount(new BigDecimal("10.00"));
        BetSelectionRequest sel1 = new BetSelectionRequest();
        sel1.setMatchId(10L);
        sel1.setPrediction(Prediction.HOME_WIN);
        BetSelectionRequest sel2 = new BetSelectionRequest();
        sel2.setMatchId(10L);
        sel2.setPrediction(Prediction.AWAY_WIN);
        request.setSelections(List.of(sel1, sel2));

        assertThatThrownBy(() -> betService.placeCombo("user@test.com", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void placeCombo_computesTotalOddsAsProductAndDeductsStakeOnce() {
        Match match1 = openMatch(10L, "Home1", "Away1");
        Match match2 = openMatch(11L, "Home2", "Away2");
        match2.setHomeOdds(new BigDecimal("1.50"));
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match1));
        when(matchRepository.findById(11L)).thenReturn(Optional.of(match2));

        ComboBetRequest request = new ComboBetRequest();
        request.setAmount(new BigDecimal("100.00"));
        BetSelectionRequest sel1 = new BetSelectionRequest();
        sel1.setMatchId(10L);
        sel1.setPrediction(Prediction.HOME_WIN); // odds 2.00
        BetSelectionRequest sel2 = new BetSelectionRequest();
        sel2.setMatchId(11L);
        sel2.setPrediction(Prediction.HOME_WIN); // odds 1.50
        request.setSelections(List.of(sel1, sel2));

        BetResponse response = betService.placeCombo("user@test.com", request);

        assertThat(response.getBetType()).isEqualTo(BetType.COMBO);
        assertThat(response.getTotalOdds()).isEqualByComparingTo("3.00"); // 2.00 * 1.50
        assertThat(response.getPossibleWin()).isEqualByComparingTo("300.00");
        assertThat(response.getSelections()).hasSize(2);
        assertThat(user.getBalance()).isEqualByComparingTo("900.00");
    }

    @Test
    void placeCombo_rejectsWhenAnySelectionMatchNotBettingOpen() {
        Match match1 = openMatch(10L, "Home1", "Away1");
        Match match2 = openMatch(11L, "Home2", "Away2");
        match2.setStatus(MatchStatus.SCHEDULED);
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match1));
        when(matchRepository.findById(11L)).thenReturn(Optional.of(match2));

        ComboBetRequest request = new ComboBetRequest();
        request.setAmount(new BigDecimal("100.00"));
        BetSelectionRequest sel1 = new BetSelectionRequest();
        sel1.setMatchId(10L);
        sel1.setPrediction(Prediction.HOME_WIN);
        BetSelectionRequest sel2 = new BetSelectionRequest();
        sel2.setMatchId(11L);
        sel2.setPrediction(Prediction.HOME_WIN);
        request.setSelections(List.of(sel1, sel2));

        assertThatThrownBy(() -> betService.placeCombo("user@test.com", request))
                .isInstanceOf(IllegalStateException.class);
    }

    private Bet comboBet(BigDecimal amount, BigDecimal totalOdds, BetSelection... selections) {
        Bet combo = new Bet();
        combo.setUser(user);
        combo.setBetType(BetType.COMBO);
        combo.setAmount(amount);
        combo.setTotalOdds(totalOdds);
        combo.setPossibleWin(amount.multiply(totalOdds));
        combo.setStatus(BetStatus.OPEN);
        List<BetSelection> list = new ArrayList<>(List.of(selections));
        for (BetSelection s : list) {
            s.setBet(combo);
        }
        combo.setSelections(list);
        return combo;
    }

    private BetSelection selectionFor(Match match, Prediction prediction, BigDecimal odds) {
        BetSelection selection = new BetSelection();
        selection.setMatch(match);
        selection.setPrediction(prediction);
        selection.setOddsSnapshot(odds);
        return selection;
    }

    @Test
    void settleBetsForMatch_settlesComboAsWonWhenAllSelectionsWin() {
        Match match1 = finishedMatch(10L, 2, 0, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));
        Match match2 = finishedMatch(11L, 1, 1, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));

        Bet combo = comboBet(new BigDecimal("10.00"), new BigDecimal("6.00"),
                selectionFor(match1, Prediction.HOME_WIN, new BigDecimal("2.00")),
                selectionFor(match2, Prediction.DRAW, new BigDecimal("3.00")));
        combo.setPossibleWin(new BigDecimal("60.00"));

        when(betRepository.findByMatch_IdAndStatus(11L, BetStatus.OPEN)).thenReturn(List.of());
        when(betSelectionRepository.findByMatch_IdAndBet_Status(11L, BetStatus.OPEN))
                .thenReturn(List.of(combo.getSelections().get(1)));

        BigDecimal balanceBefore = user.getBalance();
        betService.settleBetsForMatch(match2);

        assertThat(combo.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(combo.getProfit()).isEqualByComparingTo("50.00");
        assertThat(user.getBalance()).isEqualByComparingTo(balanceBefore.add(new BigDecimal("60.00")));
    }

    @Test
    void settleBetsForMatch_settlesComboAsLostWhenAnySelectionLoses() {
        Match match1 = finishedMatch(10L, 2, 0, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));
        Match match2 = openMatch(11L, "Home2", "Away2"); // still pending

        Bet combo = comboBet(new BigDecimal("10.00"), new BigDecimal("6.00"),
                selectionFor(match1, Prediction.AWAY_WIN, new BigDecimal("4.00")), // wrong prediction -> lost
                selectionFor(match2, Prediction.HOME_WIN, new BigDecimal("2.00")));

        when(betRepository.findByMatch_IdAndStatus(10L, BetStatus.OPEN)).thenReturn(List.of());
        when(betSelectionRepository.findByMatch_IdAndBet_Status(10L, BetStatus.OPEN))
                .thenReturn(List.of(combo.getSelections().get(0)));

        betService.settleBetsForMatch(match1);

        assertThat(combo.getStatus()).isEqualTo(BetStatus.LOST);
        assertThat(combo.getProfit()).isEqualByComparingTo("-10.00");
    }

    @Test
    void settleBetsForMatch_leavesComboOpenWhilePending() {
        Match match1 = finishedMatch(10L, 2, 0, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));
        Match match2 = openMatch(11L, "Home2", "Away2"); // still pending

        Bet combo = comboBet(new BigDecimal("10.00"), new BigDecimal("6.00"),
                selectionFor(match1, Prediction.HOME_WIN, new BigDecimal("2.00")), // correct so far
                selectionFor(match2, Prediction.HOME_WIN, new BigDecimal("3.00")));

        when(betRepository.findByMatch_IdAndStatus(10L, BetStatus.OPEN)).thenReturn(List.of());
        when(betSelectionRepository.findByMatch_IdAndBet_Status(10L, BetStatus.OPEN))
                .thenReturn(List.of(combo.getSelections().get(0)));

        betService.settleBetsForMatch(match1);

        assertThat(combo.getStatus()).isEqualTo(BetStatus.OPEN);
        assertThat(combo.getProfit()).isNull();
    }

    @Test
    void settleComboIfDecided_isIdempotent() {
        Match match1 = finishedMatch(10L, 2, 0, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));
        Match match2 = finishedMatch(11L, 1, 1, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));

        Bet combo = comboBet(new BigDecimal("10.00"), new BigDecimal("6.00"),
                selectionFor(match1, Prediction.HOME_WIN, new BigDecimal("2.00")),
                selectionFor(match2, Prediction.DRAW, new BigDecimal("3.00")));
        combo.setPossibleWin(new BigDecimal("60.00"));

        when(betRepository.findByMatch_IdAndStatus(anyLong(), eq(BetStatus.OPEN))).thenReturn(List.of());
        when(betSelectionRepository.findByMatch_IdAndBet_Status(eq(10L), eq(BetStatus.OPEN)))
                .thenReturn(List.of(combo.getSelections().get(0)));
        when(betSelectionRepository.findByMatch_IdAndBet_Status(eq(11L), eq(BetStatus.OPEN)))
                .thenReturn(List.of(combo.getSelections().get(1)));

        BigDecimal balanceBefore = user.getBalance();
        betService.settleBetsForMatch(match1);
        betService.settleBetsForMatch(match2);
        BigDecimal balanceAfterFirstSettlement = user.getBalance();

        // Re-running settlement for the same matches must not double-pay (guarded by status != OPEN)
        betService.settleBetsForMatch(match1);
        betService.settleBetsForMatch(match2);

        assertThat(combo.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(user.getBalance()).isEqualByComparingTo(balanceAfterFirstSettlement);
        assertThat(balanceAfterFirstSettlement).isEqualByComparingTo(balanceBefore.add(new BigDecimal("60.00")));
    }

    // ─── Edit bets ────────────────────────────────────────────────────────

    private Bet singleBet(long id, Match match, Prediction prediction, BigDecimal amount, BigDecimal odds) {
        Bet bet = new Bet();
        bet.setId(id);
        bet.setUser(user);
        bet.setBetType(BetType.SINGLE);
        bet.setMatch(match);
        bet.setPrediction(prediction);
        bet.setAmount(amount);
        bet.setOdds(odds);
        bet.setPossibleWin(amount.multiply(odds));
        bet.setStatus(BetStatus.OPEN);
        return bet;
    }

    private Bet correctScoreBet(long id, Match match, int homeGoals, int awayGoals, BigDecimal amount, BigDecimal odds) {
        Bet bet = new Bet();
        bet.setId(id);
        bet.setUser(user);
        bet.setBetType(BetType.SINGLE);
        bet.setMarket(BetMarket.CORRECT_SCORE);
        bet.setMatch(match);
        bet.setPredictedHomeGoals(homeGoals);
        bet.setPredictedAwayGoals(awayGoals);
        bet.setAmount(amount);
        bet.setOdds(odds);
        bet.setPossibleWin(amount.multiply(odds));
        bet.setStatus(BetStatus.OPEN);
        return bet;
    }

    @Test
    void editBet_increasingAmountDeductsDeltaFromBalance() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = singleBet(5L, match, Prediction.HOME_WIN, new BigDecimal("100.00"), new BigDecimal("2.00"));
        when(betRepository.findById(5L)).thenReturn(Optional.of(bet));

        UpdateBetRequest request = new UpdateBetRequest();
        request.setAmount(new BigDecimal("150.00"));
        request.setPrediction(Prediction.HOME_WIN);

        BetResponse response = betService.editBet("user@test.com", 5L, request);

        assertThat(user.getBalance()).isEqualByComparingTo("950.00"); // 1000 - 50 extra
        assertThat(response.getOdds()).isEqualByComparingTo("2.00"); // unchanged: prediction unchanged
        assertThat(response.getPossibleWin()).isEqualByComparingTo("300.00");
    }

    @Test
    void editBet_decreasingAmountRefundsDeltaToBalance() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = singleBet(5L, match, Prediction.HOME_WIN, new BigDecimal("100.00"), new BigDecimal("2.00"));
        when(betRepository.findById(5L)).thenReturn(Optional.of(bet));

        UpdateBetRequest request = new UpdateBetRequest();
        request.setAmount(new BigDecimal("40.00"));
        request.setPrediction(Prediction.HOME_WIN);

        betService.editBet("user@test.com", 5L, request);

        assertThat(user.getBalance()).isEqualByComparingTo("1060.00"); // 1000 + 60 refunded
    }

    @Test
    void editBet_changingPredictionResnapshotsOdds() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = singleBet(5L, match, Prediction.HOME_WIN, new BigDecimal("100.00"), new BigDecimal("2.00"));
        when(betRepository.findById(5L)).thenReturn(Optional.of(bet));

        UpdateBetRequest request = new UpdateBetRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setPrediction(Prediction.AWAY_WIN); // current away odds = 4.00

        BetResponse response = betService.editBet("user@test.com", 5L, request);

        assertThat(response.getPrediction()).isEqualTo(Prediction.AWAY_WIN);
        assertThat(response.getOdds()).isEqualByComparingTo("4.00");
        assertThat(response.getPossibleWin()).isEqualByComparingTo("400.00");
    }

    @Test
    void editBet_rejectsWhenBetIsNotOpen() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = singleBet(5L, match, Prediction.HOME_WIN, new BigDecimal("100.00"), new BigDecimal("2.00"));
        bet.setStatus(BetStatus.WON);
        when(betRepository.findById(5L)).thenReturn(Optional.of(bet));

        UpdateBetRequest request = new UpdateBetRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setPrediction(Prediction.HOME_WIN);

        assertThatThrownBy(() -> betService.editBet("user@test.com", 5L, request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void editBet_rejectsWhenMatchNoLongerBettingOpen() {
        Match match = openMatch(10L, "Home", "Away");
        match.setStatus(MatchStatus.IN_PROGRESS);
        Bet bet = singleBet(5L, match, Prediction.HOME_WIN, new BigDecimal("100.00"), new BigDecimal("2.00"));
        when(betRepository.findById(5L)).thenReturn(Optional.of(bet));

        UpdateBetRequest request = new UpdateBetRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setPrediction(Prediction.HOME_WIN);

        assertThatThrownBy(() -> betService.editBet("user@test.com", 5L, request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void editBet_rejectsWhenIncreaseExceedsBalance() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = singleBet(5L, match, Prediction.HOME_WIN, new BigDecimal("100.00"), new BigDecimal("2.00"));
        when(betRepository.findById(5L)).thenReturn(Optional.of(bet));

        UpdateBetRequest request = new UpdateBetRequest();
        request.setAmount(new BigDecimal("5000.00"));
        request.setPrediction(Prediction.HOME_WIN);

        assertThatThrownBy(() -> betService.editBet("user@test.com", 5L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Ownership checks ─────────────────────────────────────────────────

    @Test
    void editBet_userACannotEditUserBBet() {
        User userB = new User();
        userB.setId(2L);

        Match match = openMatch(10L, "Home", "Away");

        Bet bet = new Bet();
        bet.setId(99L);
        bet.setUser(userB);
        bet.setBetType(BetType.SINGLE);
        bet.setMarket(BetMarket.MATCH_RESULT);
        bet.setStatus(BetStatus.OPEN);
        bet.setMatch(match);
        bet.setPrediction(Prediction.HOME_WIN);
        bet.setAmount(BigDecimal.TEN);
        bet.setOdds(new BigDecimal("2.00"));
        bet.setPossibleWin(new BigDecimal("20.00"));

        when(betRepository.findById(99L)).thenReturn(Optional.of(bet));

        UpdateBetRequest req = new UpdateBetRequest();
        req.setAmount(BigDecimal.TEN);
        req.setPrediction(Prediction.AWAY_WIN);

        assertThatThrownBy(() -> betService.editBet("user@test.com", 99L, req))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void cancelBet_userACannotCancelUserBBet() {
        User userB = new User();
        userB.setId(2L);

        Match match = openMatch(10L, "Home", "Away");

        Bet bet = new Bet();
        bet.setId(99L);
        bet.setUser(userB);
        bet.setBetType(BetType.SINGLE);
        bet.setMarket(BetMarket.MATCH_RESULT);
        bet.setStatus(BetStatus.OPEN);
        bet.setMatch(match);
        bet.setAmount(BigDecimal.TEN);

        when(betRepository.findById(99L)).thenReturn(Optional.of(bet));

        assertThatThrownBy(() -> betService.cancelBet("user@test.com", 99L))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    // ─── Cancel bets ──────────────────────────────────────────────────────

    @Test
    void cancelBet_refundsFullStakeAndMarksCancelled() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = singleBet(5L, match, Prediction.HOME_WIN, new BigDecimal("100.00"), new BigDecimal("2.00"));
        when(betRepository.findById(5L)).thenReturn(Optional.of(bet));

        BetResponse response = betService.cancelBet("user@test.com", 5L);

        assertThat(response.getStatus()).isEqualTo(BetStatus.CANCELLED);
        assertThat(user.getBalance()).isEqualByComparingTo("1100.00");
        assertThat(bet.getSettledAt()).isNotNull();
    }

    @Test
    void cancelBet_refundsComboStakeInFull() {
        Match match1 = openMatch(10L, "Home1", "Away1");
        Match match2 = openMatch(11L, "Home2", "Away2");
        Bet combo = comboBet(new BigDecimal("50.00"), new BigDecimal("4.00"),
                selectionFor(match1, Prediction.HOME_WIN, new BigDecimal("2.00")),
                selectionFor(match2, Prediction.HOME_WIN, new BigDecimal("2.00")));
        combo.setId(7L);
        when(betRepository.findById(7L)).thenReturn(Optional.of(combo));

        betService.cancelBet("user@test.com", 7L);

        assertThat(combo.getStatus()).isEqualTo(BetStatus.CANCELLED);
        assertThat(user.getBalance()).isEqualByComparingTo("1050.00");
    }

    @Test
    void cancelBet_rejectsWhenBetAlreadySettled() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = singleBet(5L, match, Prediction.HOME_WIN, new BigDecimal("100.00"), new BigDecimal("2.00"));
        bet.setStatus(BetStatus.WON);
        when(betRepository.findById(5L)).thenReturn(Optional.of(bet));

        assertThatThrownBy(() -> betService.cancelBet("user@test.com", 5L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelBet_rejectsWhenMatchNoLongerOpenForBetting() {
        Match match = openMatch(10L, "Home", "Away");
        match.setStatus(MatchStatus.IN_PROGRESS);
        Bet bet = singleBet(5L, match, Prediction.HOME_WIN, new BigDecimal("100.00"), new BigDecimal("2.00"));
        when(betRepository.findById(5L)).thenReturn(Optional.of(bet));

        assertThatThrownBy(() -> betService.cancelBet("user@test.com", 5L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(bet.getStatus()).isEqualTo(BetStatus.OPEN);
        assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void cancelBet_ignoredByLaterSettlement() {
        Match match = finishedMatch(10L, 2, 0, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));
        Bet bet = singleBet(5L, match, Prediction.HOME_WIN, new BigDecimal("100.00"), new BigDecimal("2.00"));
        bet.setStatus(BetStatus.CANCELLED);
        bet.setProfit(null);

        // Cancelled bets are excluded by the OPEN-status repository query, so settlement never touches them.
        when(betRepository.findByMatch_IdAndStatus(10L, BetStatus.OPEN)).thenReturn(List.of());
        when(betSelectionRepository.findByMatch_IdAndBet_Status(10L, BetStatus.OPEN)).thenReturn(List.of());

        betService.settleBetsForMatch(match);

        assertThat(bet.getStatus()).isEqualTo(BetStatus.CANCELLED);
        assertThat(bet.getProfit()).isNull();
    }

    // ─── Correct score bets ───────────────────────────────────────────────

    @Test
    void placeCorrectScoreBet_deductsBalanceAndSnapshotsOddsAndPrediction() {
        Match match = openMatch(10L, "Home", "Away");
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));
        BigDecimal expectedOdds = oddsService.computeCorrectScoreOdds(match, 2, 1);

        CorrectScoreBetRequest request = new CorrectScoreBetRequest();
        request.setMatchId(10L);
        request.setHomeGoals(2);
        request.setAwayGoals(1);
        request.setAmount(new BigDecimal("20.00"));

        BetResponse response = betService.placeCorrectScoreBet("user@test.com", request);

        assertThat(user.getBalance()).isEqualByComparingTo("980.00");
        assertThat(response.getBetType()).isEqualTo(BetType.SINGLE);
        assertThat(response.getMarket()).isEqualTo(BetMarket.CORRECT_SCORE);
        assertThat(response.getPredictedHomeGoals()).isEqualTo(2);
        assertThat(response.getPredictedAwayGoals()).isEqualTo(1);
        assertThat(response.getOdds()).isEqualByComparingTo(expectedOdds);
        assertThat(response.getPossibleWin()).isEqualByComparingTo(
                new BigDecimal("20.00").multiply(expectedOdds).setScale(2, RoundingMode.HALF_UP));
        assertThat(response.getDisplayLabel()).isEqualTo("Correct Score 2-1");
    }

    @Test
    void placeCorrectScoreBet_rejectsWhenMatchNotBettingOpen() {
        Match match = openMatch(10L, "Home", "Away");
        match.setStatus(MatchStatus.SCHEDULED);
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        CorrectScoreBetRequest request = new CorrectScoreBetRequest();
        request.setMatchId(10L);
        request.setHomeGoals(1);
        request.setAwayGoals(0);
        request.setAmount(new BigDecimal("10.00"));

        assertThatThrownBy(() -> betService.placeCorrectScoreBet("user@test.com", request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void placeCorrectScoreBet_rejectsDuplicateBetOnSameMatch() {
        Match match = openMatch(10L, "Home", "Away");
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        Bet existing = new Bet();
        existing.setBetType(BetType.SINGLE);
        existing.setMatch(match);
        when(betRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(1L, BetStatus.OPEN))
                .thenReturn(List.of(existing));

        CorrectScoreBetRequest request = new CorrectScoreBetRequest();
        request.setMatchId(10L);
        request.setHomeGoals(1);
        request.setAwayGoals(0);
        request.setAmount(new BigDecimal("10.00"));

        assertThatThrownBy(() -> betService.placeCorrectScoreBet("user@test.com", request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void placeCorrectScoreBet_rejectsWhenAmountExceedsBalance() {
        Match match = openMatch(10L, "Home", "Away");
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        CorrectScoreBetRequest request = new CorrectScoreBetRequest();
        request.setMatchId(10L);
        request.setHomeGoals(1);
        request.setAwayGoals(0);
        request.setAmount(new BigDecimal("5000.00"));

        assertThatThrownBy(() -> betService.placeCorrectScoreBet("user@test.com", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settleBetsForMatch_settlesCorrectScoreBetsAsWonAndLost() {
        Match match = finishedMatch(10L, 2, 1, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));

        User winnerUser = new User();
        winnerUser.setId(2L);
        winnerUser.setBalance(new BigDecimal("100.00"));
        Bet winningBet = correctScoreBet(20L, match, 2, 1, new BigDecimal("10.00"), new BigDecimal("8.50"));
        winningBet.setUser(winnerUser);

        User loserUser = new User();
        loserUser.setId(3L);
        loserUser.setBalance(new BigDecimal("100.00"));
        Bet losingBet = correctScoreBet(21L, match, 1, 1, new BigDecimal("10.00"), new BigDecimal("6.00"));
        losingBet.setUser(loserUser);

        when(betRepository.findByMatch_IdAndStatus(10L, BetStatus.OPEN)).thenReturn(List.of(winningBet, losingBet));
        when(betSelectionRepository.findByMatch_IdAndBet_Status(10L, BetStatus.OPEN)).thenReturn(List.of());

        betService.settleBetsForMatch(match);

        assertThat(winningBet.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(winningBet.getProfit()).isEqualByComparingTo("75.00"); // 85 - 10
        assertThat(winnerUser.getBalance()).isEqualByComparingTo("185.00"); // 100 + 85

        assertThat(losingBet.getStatus()).isEqualTo(BetStatus.LOST);
        assertThat(losingBet.getProfit()).isEqualByComparingTo("-10.00");
        assertThat(loserUser.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void settleBetsForMatch_correctScoreSettlementIsIdempotent() {
        Match match = finishedMatch(10L, 3, 0, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));
        Bet bet = correctScoreBet(20L, match, 3, 0, new BigDecimal("10.00"), new BigDecimal("12.00"));

        // First call sees the OPEN bet; once settled, the OPEN-status repository query no longer returns it.
        when(betRepository.findByMatch_IdAndStatus(10L, BetStatus.OPEN))
                .thenReturn(List.of(bet))
                .thenReturn(List.of());
        when(betSelectionRepository.findByMatch_IdAndBet_Status(10L, BetStatus.OPEN)).thenReturn(List.of());

        BigDecimal balanceBefore = user.getBalance();
        betService.settleBetsForMatch(match);
        BigDecimal balanceAfterFirst = user.getBalance();

        betService.settleBetsForMatch(match);

        assertThat(bet.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(balanceAfterFirst).isEqualByComparingTo(balanceBefore.add(new BigDecimal("120.00")));
        assertThat(user.getBalance()).isEqualByComparingTo(balanceAfterFirst);
    }

    @Test
    void cancelBet_refundsCorrectScoreStakeAndMarksCancelled() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = correctScoreBet(20L, match, 2, 1, new BigDecimal("30.00"), new BigDecimal("8.50"));
        when(betRepository.findById(20L)).thenReturn(Optional.of(bet));

        BetResponse response = betService.cancelBet("user@test.com", 20L);

        assertThat(response.getStatus()).isEqualTo(BetStatus.CANCELLED);
        assertThat(user.getBalance()).isEqualByComparingTo("1030.00");
        assertThat(bet.getSettledAt()).isNotNull();
    }

    @Test
    void editCorrectScoreBet_increasingAmountDeductsDeltaFromBalance() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = correctScoreBet(20L, match, 2, 1, new BigDecimal("10.00"), new BigDecimal("8.50"));
        when(betRepository.findById(20L)).thenReturn(Optional.of(bet));

        UpdateCorrectScoreBetRequest request = new UpdateCorrectScoreBetRequest();
        request.setAmount(new BigDecimal("25.00"));
        request.setHomeGoals(2);
        request.setAwayGoals(1);

        BetResponse response = betService.editCorrectScoreBet("user@test.com", 20L, request);

        assertThat(user.getBalance()).isEqualByComparingTo("985.00"); // 1000 - 15 extra
        assertThat(response.getOdds()).isEqualByComparingTo("8.50"); // unchanged: score unchanged
        assertThat(response.getPossibleWin()).isEqualByComparingTo("212.50");
    }

    @Test
    void editCorrectScoreBet_decreasingAmountRefundsDeltaToBalance() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = correctScoreBet(20L, match, 2, 1, new BigDecimal("10.00"), new BigDecimal("8.50"));
        when(betRepository.findById(20L)).thenReturn(Optional.of(bet));

        UpdateCorrectScoreBetRequest request = new UpdateCorrectScoreBetRequest();
        request.setAmount(new BigDecimal("4.00"));
        request.setHomeGoals(2);
        request.setAwayGoals(1);

        betService.editCorrectScoreBet("user@test.com", 20L, request);

        assertThat(user.getBalance()).isEqualByComparingTo("1006.00"); // 1000 + 6 refunded
    }

    @Test
    void editCorrectScoreBet_changingScoreResnapshotsOddsAndPossibleWin() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = correctScoreBet(20L, match, 2, 1, new BigDecimal("10.00"), new BigDecimal("8.50"));
        when(betRepository.findById(20L)).thenReturn(Optional.of(bet));
        BigDecimal expectedOdds = oddsService.computeCorrectScoreOdds(match, 3, 2);

        UpdateCorrectScoreBetRequest request = new UpdateCorrectScoreBetRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setHomeGoals(3);
        request.setAwayGoals(2);

        BetResponse response = betService.editCorrectScoreBet("user@test.com", 20L, request);

        assertThat(response.getPredictedHomeGoals()).isEqualTo(3);
        assertThat(response.getPredictedAwayGoals()).isEqualTo(2);
        assertThat(response.getOdds()).isEqualByComparingTo(expectedOdds);
        assertThat(response.getPossibleWin()).isEqualByComparingTo(
                new BigDecimal("10.00").multiply(expectedOdds).setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void editCorrectScoreBet_rejectsWhenBetIsNotOpen() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = correctScoreBet(20L, match, 2, 1, new BigDecimal("10.00"), new BigDecimal("8.50"));
        bet.setStatus(BetStatus.WON);
        when(betRepository.findById(20L)).thenReturn(Optional.of(bet));

        UpdateCorrectScoreBetRequest request = new UpdateCorrectScoreBetRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setHomeGoals(2);
        request.setAwayGoals(1);

        assertThatThrownBy(() -> betService.editCorrectScoreBet("user@test.com", 20L, request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void editCorrectScoreBet_rejectsWhenMatchNoLongerBettingOpen() {
        Match match = openMatch(10L, "Home", "Away");
        match.setStatus(MatchStatus.IN_PROGRESS);
        Bet bet = correctScoreBet(20L, match, 2, 1, new BigDecimal("10.00"), new BigDecimal("8.50"));
        when(betRepository.findById(20L)).thenReturn(Optional.of(bet));

        UpdateCorrectScoreBetRequest request = new UpdateCorrectScoreBetRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setHomeGoals(2);
        request.setAwayGoals(1);

        assertThatThrownBy(() -> betService.editCorrectScoreBet("user@test.com", 20L, request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void editCorrectScoreBet_rejectsWhenIncreaseExceedsBalance() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = correctScoreBet(20L, match, 2, 1, new BigDecimal("10.00"), new BigDecimal("8.50"));
        when(betRepository.findById(20L)).thenReturn(Optional.of(bet));

        UpdateCorrectScoreBetRequest request = new UpdateCorrectScoreBetRequest();
        request.setAmount(new BigDecimal("5000.00"));
        request.setHomeGoals(2);
        request.setAwayGoals(1);

        assertThatThrownBy(() -> betService.editCorrectScoreBet("user@test.com", 20L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editCorrectScoreBet_rejectsWhenBetIsMatchResultMarket() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = singleBet(5L, match, Prediction.HOME_WIN, new BigDecimal("100.00"), new BigDecimal("2.00"));
        when(betRepository.findById(5L)).thenReturn(Optional.of(bet));

        UpdateCorrectScoreBetRequest request = new UpdateCorrectScoreBetRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setHomeGoals(1);
        request.setAwayGoals(0);

        assertThatThrownBy(() -> betService.editCorrectScoreBet("user@test.com", 5L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editBet_rejectsWhenBetIsCorrectScoreMarket() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = correctScoreBet(20L, match, 2, 1, new BigDecimal("10.00"), new BigDecimal("8.50"));
        when(betRepository.findById(20L)).thenReturn(Optional.of(bet));

        UpdateBetRequest request = new UpdateBetRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setPrediction(Prediction.HOME_WIN);

        assertThatThrownBy(() -> betService.editBet("user@test.com", 20L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Handicap bets (Milestone 25) ────────────────────────────────────

    private Bet handicapBet(long id, Match match, HandicapSelection selection, BigDecimal amount, BigDecimal odds) {
        Bet bet = new Bet();
        bet.setId(id);
        bet.setUser(user);
        bet.setBetType(BetType.SINGLE);
        bet.setMarket(BetMarket.HANDICAP);
        bet.setMatch(match);
        bet.setHandicapSelection(selection);
        bet.setAmount(amount);
        bet.setOdds(odds);
        bet.setPossibleWin(amount.multiply(odds).setScale(2, java.math.RoundingMode.HALF_UP));
        bet.setStatus(BetStatus.OPEN);
        return bet;
    }

    private static void freezeHandicap(Match match, String homeMinus1, String hcpDraw, String awayPlus1) {
        match.setHandicapHomeMinusOneOdds(new BigDecimal(homeMinus1));
        match.setHandicapDrawOdds(new BigDecimal(hcpDraw));
        match.setHandicapAwayPlusOneOdds(new BigDecimal(awayPlus1));
    }

    @Test
    void placeHandicapBet_deductsBalanceAndStoresMarketSelectionOddsAndPossibleWin() {
        Match match = openMatch(10L, "Home", "Away");
        freezeHandicap(match, "2.35", "3.90", "1.95");
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));
        // Milestone 41: the booked odds are exactly the frozen market price displayed to the user
        BigDecimal expectedOdds = new BigDecimal("2.35");

        HandicapBetRequest request = new HandicapBetRequest();
        request.setMatchId(10L);
        request.setSelection(HandicapSelection.HOME_MINUS_ONE);
        request.setAmount(new BigDecimal("50.00"));

        BetResponse response = betService.placeHandicapBet("user@test.com", request);

        assertThat(user.getBalance()).isEqualByComparingTo("950.00");
        assertThat(response.getBetType()).isEqualTo(BetType.SINGLE);
        assertThat(response.getMarket()).isEqualTo(BetMarket.HANDICAP);
        assertThat(response.getHandicapSelection()).isEqualTo(HandicapSelection.HOME_MINUS_ONE);
        assertThat(response.getOdds()).isEqualByComparingTo(expectedOdds);
        assertThat(response.getPossibleWin()).isEqualByComparingTo(
                new BigDecimal("50.00").multiply(expectedOdds).setScale(2, RoundingMode.HALF_UP));
        assertThat(response.getDisplayLabel()).isEqualTo("Home -1");
    }

    @Test
    void placeHandicapBet_rejectsWhenMatchNotBettingOpen() {
        Match match = openMatch(10L, "Home", "Away");
        match.setStatus(MatchStatus.SCHEDULED);
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        HandicapBetRequest request = new HandicapBetRequest();
        request.setMatchId(10L);
        request.setSelection(HandicapSelection.HANDICAP_DRAW);
        request.setAmount(new BigDecimal("20.00"));

        assertThatThrownBy(() -> betService.placeHandicapBet("user@test.com", request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void placeHandicapBet_rejectsDuplicateBetOnSameMatch() {
        Match match = openMatch(10L, "Home", "Away");
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        Bet existing = new Bet();
        existing.setBetType(BetType.SINGLE);
        existing.setMatch(match);
        when(betRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(1L, BetStatus.OPEN))
                .thenReturn(List.of(existing));

        HandicapBetRequest request = new HandicapBetRequest();
        request.setMatchId(10L);
        request.setSelection(HandicapSelection.AWAY_PLUS_ONE);
        request.setAmount(new BigDecimal("20.00"));

        assertThatThrownBy(() -> betService.placeHandicapBet("user@test.com", request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void placeHandicapBet_rejectsWhenAmountExceedsBalance() {
        Match match = openMatch(10L, "Home", "Away");
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        HandicapBetRequest request = new HandicapBetRequest();
        request.setMatchId(10L);
        request.setSelection(HandicapSelection.HOME_MINUS_ONE);
        request.setAmount(new BigDecimal("9999.00"));

        assertThatThrownBy(() -> betService.placeHandicapBet("user@test.com", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settleBetsForMatch_homeMinus1WinsOnlyWhenHomeWinsBy2OrMore() {
        // 2-0: h-1=1 > a=0 → HOME_MINUS_ONE wins
        Match match = finishedMatch(10L, 2, 0, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));

        User winner = new User(); winner.setId(2L); winner.setBalance(new BigDecimal("0.00"));
        Bet winBet = handicapBet(30L, match, HandicapSelection.HOME_MINUS_ONE, new BigDecimal("10.00"), new BigDecimal("2.50"));
        winBet.setUser(winner);

        User loser = new User(); loser.setId(3L); loser.setBalance(new BigDecimal("0.00"));
        Bet loseBet = handicapBet(31L, match, HandicapSelection.HANDICAP_DRAW, new BigDecimal("10.00"), new BigDecimal("3.80"));
        loseBet.setUser(loser);

        when(betRepository.findByMatch_IdAndStatus(10L, BetStatus.OPEN)).thenReturn(List.of(winBet, loseBet));
        when(betSelectionRepository.findByMatch_IdAndBet_Status(10L, BetStatus.OPEN)).thenReturn(List.of());

        betService.settleBetsForMatch(match);

        assertThat(winBet.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(winBet.getProfit()).isEqualByComparingTo("15.00"); // 25 - 10
        assertThat(winner.getBalance()).isEqualByComparingTo("25.00");

        assertThat(loseBet.getStatus()).isEqualTo(BetStatus.LOST);
        assertThat(loseBet.getProfit()).isEqualByComparingTo("-10.00");
    }

    @Test
    void settleBetsForMatch_handicapDrawWinsOnlyWhenHomeWinsByExactlyOne() {
        // 1-0: h-1=0 == a=0 → HANDICAP_DRAW wins
        Match match = finishedMatch(10L, 1, 0, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));

        User winner = new User(); winner.setId(2L); winner.setBalance(new BigDecimal("0.00"));
        Bet winBet = handicapBet(30L, match, HandicapSelection.HANDICAP_DRAW, new BigDecimal("10.00"), new BigDecimal("3.80"));
        winBet.setUser(winner);

        User loser = new User(); loser.setId(3L); loser.setBalance(new BigDecimal("0.00"));
        Bet loseBet = handicapBet(31L, match, HandicapSelection.HOME_MINUS_ONE, new BigDecimal("10.00"), new BigDecimal("2.50"));
        loseBet.setUser(loser);

        when(betRepository.findByMatch_IdAndStatus(10L, BetStatus.OPEN)).thenReturn(List.of(winBet, loseBet));
        when(betSelectionRepository.findByMatch_IdAndBet_Status(10L, BetStatus.OPEN)).thenReturn(List.of());

        betService.settleBetsForMatch(match);

        assertThat(winBet.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(loser.getBalance()).isEqualByComparingTo("0.00"); // loser unchanged
    }

    @Test
    void settleBetsForMatch_awayPlus1WinsOnDraw_awayWin_orHomeFailsToWinByOne() {
        // 1-1 draw: h-1=0, a=1, diff=-1 < 0 → AWAY_PLUS_ONE wins
        Match match = finishedMatch(10L, 1, 1, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));

        User winner = new User(); winner.setId(2L); winner.setBalance(new BigDecimal("0.00"));
        Bet winBet = handicapBet(30L, match, HandicapSelection.AWAY_PLUS_ONE, new BigDecimal("10.00"), new BigDecimal("2.20"));
        winBet.setUser(winner);

        User loser = new User(); loser.setId(3L); loser.setBalance(new BigDecimal("0.00"));
        Bet loseBet = handicapBet(31L, match, HandicapSelection.HOME_MINUS_ONE, new BigDecimal("10.00"), new BigDecimal("2.50"));
        loseBet.setUser(loser);

        when(betRepository.findByMatch_IdAndStatus(10L, BetStatus.OPEN)).thenReturn(List.of(winBet, loseBet));
        when(betSelectionRepository.findByMatch_IdAndBet_Status(10L, BetStatus.OPEN)).thenReturn(List.of());

        betService.settleBetsForMatch(match);

        assertThat(winBet.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(loseBet.getStatus()).isEqualTo(BetStatus.LOST);
    }

    @Test
    void settleBetsForMatch_handicapSettlementIsIdempotent() {
        Match match = finishedMatch(10L, 3, 0, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));
        Bet bet = handicapBet(30L, match, HandicapSelection.HOME_MINUS_ONE, new BigDecimal("10.00"), new BigDecimal("2.50"));

        when(betRepository.findByMatch_IdAndStatus(10L, BetStatus.OPEN))
                .thenReturn(List.of(bet))
                .thenReturn(List.of());
        when(betSelectionRepository.findByMatch_IdAndBet_Status(10L, BetStatus.OPEN)).thenReturn(List.of());

        BigDecimal balanceBefore = user.getBalance();
        betService.settleBetsForMatch(match);
        BigDecimal balanceAfterFirst = user.getBalance();
        betService.settleBetsForMatch(match);

        assertThat(bet.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(user.getBalance()).isEqualByComparingTo(balanceAfterFirst);
        assertThat(balanceAfterFirst).isEqualByComparingTo(balanceBefore.add(new BigDecimal("25.00")));
    }

    @Test
    void cancelBet_refundsHandicapStakeAndMarksCancelled() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = handicapBet(30L, match, HandicapSelection.AWAY_PLUS_ONE, new BigDecimal("40.00"), new BigDecimal("2.20"));
        when(betRepository.findById(30L)).thenReturn(Optional.of(bet));

        BetResponse response = betService.cancelBet("user@test.com", 30L);

        assertThat(response.getStatus()).isEqualTo(BetStatus.CANCELLED);
        assertThat(user.getBalance()).isEqualByComparingTo("1040.00");
        assertThat(bet.getSettledAt()).isNotNull();
    }

    @Test
    void editHandicapBet_increasingAmountDeductsDelta() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = handicapBet(30L, match, HandicapSelection.HOME_MINUS_ONE, new BigDecimal("50.00"), new BigDecimal("2.50"));
        when(betRepository.findById(30L)).thenReturn(Optional.of(bet));

        UpdateHandicapBetRequest request = new UpdateHandicapBetRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setSelection(HandicapSelection.HOME_MINUS_ONE);

        betService.editHandicapBet("user@test.com", 30L, request);

        assertThat(user.getBalance()).isEqualByComparingTo("950.00"); // 1000 - 50 extra
    }

    @Test
    void editHandicapBet_decreasingAmountRefundsDelta() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = handicapBet(30L, match, HandicapSelection.HOME_MINUS_ONE, new BigDecimal("100.00"), new BigDecimal("2.50"));
        when(betRepository.findById(30L)).thenReturn(Optional.of(bet));

        UpdateHandicapBetRequest request = new UpdateHandicapBetRequest();
        request.setAmount(new BigDecimal("40.00"));
        request.setSelection(HandicapSelection.HOME_MINUS_ONE);

        betService.editHandicapBet("user@test.com", 30L, request);

        assertThat(user.getBalance()).isEqualByComparingTo("1060.00"); // 1000 + 60 refunded
    }

    @Test
    void placeHandicapBet_rejectsWhenMarketNotFrozenYet() {
        Match match = openMatch(10L, "Home", "Away");   // BETTING_OPEN but no frozen handicap prices
        when(matchRepository.findById(10L)).thenReturn(Optional.of(match));

        HandicapBetRequest request = new HandicapBetRequest();
        request.setMatchId(10L);
        request.setSelection(HandicapSelection.HOME_MINUS_ONE);
        request.setAmount(new BigDecimal("50.00"));

        assertThatThrownBy(() -> betService.placeHandicapBet("user@test.com", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available");
        assertThat(user.getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void editHandicapBet_changingSelectionResnapshotsOddsAndPossibleWin() {
        Match match = openMatch(10L, "Home", "Away");
        freezeHandicap(match, "2.35", "3.90", "1.95");
        BigDecimal oldOdds = new BigDecimal("2.35");
        Bet bet = handicapBet(30L, match, HandicapSelection.HOME_MINUS_ONE, new BigDecimal("50.00"), oldOdds);
        when(betRepository.findById(30L)).thenReturn(Optional.of(bet));
        BigDecimal expectedNewOdds = new BigDecimal("1.95");

        UpdateHandicapBetRequest request = new UpdateHandicapBetRequest();
        request.setAmount(new BigDecimal("50.00"));
        request.setSelection(HandicapSelection.AWAY_PLUS_ONE);

        BetResponse response = betService.editHandicapBet("user@test.com", 30L, request);

        assertThat(response.getHandicapSelection()).isEqualTo(HandicapSelection.AWAY_PLUS_ONE);
        assertThat(response.getOdds()).isEqualByComparingTo(expectedNewOdds);
        assertThat(response.getPossibleWin()).isEqualByComparingTo(
                new BigDecimal("50.00").multiply(expectedNewOdds).setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void editHandicapBet_rejectsWhenBetIsNotOpen() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = handicapBet(30L, match, HandicapSelection.HOME_MINUS_ONE, new BigDecimal("50.00"), new BigDecimal("2.50"));
        bet.setStatus(BetStatus.WON);
        when(betRepository.findById(30L)).thenReturn(Optional.of(bet));

        UpdateHandicapBetRequest request = new UpdateHandicapBetRequest();
        request.setAmount(new BigDecimal("50.00"));
        request.setSelection(HandicapSelection.HOME_MINUS_ONE);

        assertThatThrownBy(() -> betService.editHandicapBet("user@test.com", 30L, request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void editHandicapBet_rejectsWhenMatchNoLongerBettingOpen() {
        Match match = openMatch(10L, "Home", "Away");
        match.setStatus(MatchStatus.IN_PROGRESS);
        Bet bet = handicapBet(30L, match, HandicapSelection.HOME_MINUS_ONE, new BigDecimal("50.00"), new BigDecimal("2.50"));
        when(betRepository.findById(30L)).thenReturn(Optional.of(bet));

        UpdateHandicapBetRequest request = new UpdateHandicapBetRequest();
        request.setAmount(new BigDecimal("50.00"));
        request.setSelection(HandicapSelection.HOME_MINUS_ONE);

        assertThatThrownBy(() -> betService.editHandicapBet("user@test.com", 30L, request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void editHandicapBet_rejectsWhenIncreaseExceedsBalance() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = handicapBet(30L, match, HandicapSelection.HOME_MINUS_ONE, new BigDecimal("50.00"), new BigDecimal("2.50"));
        when(betRepository.findById(30L)).thenReturn(Optional.of(bet));

        UpdateHandicapBetRequest request = new UpdateHandicapBetRequest();
        request.setAmount(new BigDecimal("9000.00"));
        request.setSelection(HandicapSelection.HOME_MINUS_ONE);

        assertThatThrownBy(() -> betService.editHandicapBet("user@test.com", 30L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editHandicapBet_rejectsWhenBetIsMatchResultMarket() {
        Match match = openMatch(10L, "Home", "Away");
        Bet bet = singleBet(5L, match, Prediction.HOME_WIN, new BigDecimal("50.00"), new BigDecimal("2.00"));
        when(betRepository.findById(5L)).thenReturn(Optional.of(bet));

        UpdateHandicapBetRequest request = new UpdateHandicapBetRequest();
        request.setAmount(new BigDecimal("50.00"));
        request.setSelection(HandicapSelection.HOME_MINUS_ONE);

        assertThatThrownBy(() -> betService.editHandicapBet("user@test.com", 5L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settleBetsForMatch_cancelledHandicapBetIsIgnored() {
        Match match = finishedMatch(10L, 2, 0, new BigDecimal("2.00"), new BigDecimal("3.00"), new BigDecimal("4.00"));
        Bet cancelledBet = handicapBet(30L, match, HandicapSelection.HOME_MINUS_ONE, new BigDecimal("50.00"), new BigDecimal("2.50"));
        cancelledBet.setStatus(BetStatus.CANCELLED);
        cancelledBet.setProfit(null);

        when(betRepository.findByMatch_IdAndStatus(10L, BetStatus.OPEN)).thenReturn(List.of());
        when(betSelectionRepository.findByMatch_IdAndBet_Status(10L, BetStatus.OPEN)).thenReturn(List.of());

        betService.settleBetsForMatch(match);

        assertThat(cancelledBet.getStatus()).isEqualTo(BetStatus.CANCELLED);
        assertThat(cancelledBet.getProfit()).isNull();
    }
}

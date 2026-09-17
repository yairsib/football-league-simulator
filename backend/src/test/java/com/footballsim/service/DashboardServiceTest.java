package com.footballsim.service;

import com.footballsim.dto.DashboardSummaryResponse;
import com.footballsim.entity.*;
import com.footballsim.enums.*;
import com.footballsim.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

/**
 * BetService is a concrete class that Mockito cannot inline-mock on this JDK/OS combination,
 * so we use a real BetService instance (backed by mocked repositories) — exactly the same
 * pattern used in BetServiceTest for OddsService.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private BetRepository betRepository;
    @Mock private BetSelectionRepository betSelectionRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private RoundRepository roundRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private PlayerRepository playerRepository;

    private OddsService oddsService;
    private BetService betService;
    private DashboardService dashboardService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setBalance(new BigDecimal("850.00"));
        user.setRole(Role.USER);

        oddsService = new OddsService(playerRepository, teamRepository);
        betService = new BetService(betRepository, betSelectionRepository, matchRepository,
                userRepository, playerRepository, teamRepository, roundRepository, oddsService);
        dashboardService = new DashboardService(userRepository, betRepository, matchRepository,
                roundRepository, teamRepository, playerRepository, betService);

        lenient().when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        lenient().when(betRepository.findByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        lenient().when(matchRepository.findAll()).thenReturn(List.of());
        lenient().when(roundRepository.findAll()).thenReturn(List.of());
        lenient().when(teamRepository.findAll()).thenReturn(List.of());
        lenient().when(playerRepository.findAll()).thenReturn(List.of());
        // Default: no Round 1 → season bets closed
        lenient().when(roundRepository.findByRoundNumber(1)).thenReturn(Optional.empty());
        lenient().when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(anyLong())).thenReturn(List.of());
        lenient().when(betRepository.findByUser_IdAndStatusOrderByCreatedAtDesc(anyLong(), any())).thenReturn(List.of());
    }

    @Test
    void summaryReturnsBalanceAndZeroCountsWhenNoBets() {
        DashboardSummaryResponse s = dashboardService.getSummary("user@test.com");

        assertThat(s.getBalance()).isEqualByComparingTo("850.00");
        assertThat(s.getOpenBetsCount()).isZero();
        assertThat(s.getWonBetsCount()).isZero();
        assertThat(s.getLostBetsCount()).isZero();
        assertThat(s.getCancelledBetsCount()).isZero();
        assertThat(s.getSettledBetsCount()).isZero();
        assertThat(s.getTotalStakedOpen()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getPotentialWinningsOpen()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getTotalProfitSettled()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getRecentBets()).isEmpty();
    }

    @Test
    void summaryCountsBetsCorrectly() {
        Bet openBet = openMatchBet("150.00", "1.80");
        Bet wonBet = settledBet(BetStatus.WON, "50.00", "40.00");
        Bet lostBet = settledBet(BetStatus.LOST, "30.00", "-30.00");
        Bet cancelledBet = cancelledBet("20.00");

        lenient().when(betRepository.findByUser_IdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(cancelledBet, lostBet, wonBet, openBet));

        DashboardSummaryResponse s = dashboardService.getSummary("user@test.com");

        assertThat(s.getOpenBetsCount()).isEqualTo(1);
        assertThat(s.getWonBetsCount()).isEqualTo(1);
        assertThat(s.getLostBetsCount()).isEqualTo(1);
        assertThat(s.getCancelledBetsCount()).isEqualTo(1);
        assertThat(s.getSettledBetsCount()).isEqualTo(2);
        assertThat(s.getTotalStakedOpen()).isEqualByComparingTo("150.00");
        assertThat(s.getPotentialWinningsOpen()).isEqualByComparingTo("270.00"); // 150 * 1.80
        assertThat(s.getTotalProfitSettled()).isEqualByComparingTo("10.00"); // 40 - 30
    }

    @Test
    void recentBetsLimitedToFive() {
        List<Bet> bets = new ArrayList<>();
        for (int i = 0; i < 6; i++) bets.add(openMatchBet("10.00", "2.00"));
        lenient().when(betRepository.findByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(bets);

        DashboardSummaryResponse s = dashboardService.getSummary("user@test.com");

        assertThat(s.getRecentBets()).hasSize(5);
    }

    @Test
    void summaryIncludesSeasonBetsStatusOpen() {
        // Round 1 exists with OPEN_FOR_BETS (not FINISHED) and a SCHEDULED match → open
        Round r1 = round(1, RoundStatus.OPEN_FOR_BETS);
        lenient().when(roundRepository.findByRoundNumber(1)).thenReturn(Optional.of(r1));
        lenient().when(matchRepository.findByRound_RoundNumberOrderByIdAsc(1))
                .thenReturn(List.of(scheduledMatch()));

        DashboardSummaryResponse s = dashboardService.getSummary("user@test.com");

        assertThat(s.isSeasonBetsOpen()).isTrue();
        assertThat(s.getSeasonBetsStatusReason()).contains("open");
        assertThat(s.isCanPlaceSeasonBets()).isTrue();
    }

    @Test
    void summarySeasonBetsLockedWhenRound1NotFound() {
        // Default setUp: roundRepository.findByRoundNumber(1) returns empty
        DashboardSummaryResponse s = dashboardService.getSummary("user@test.com");

        assertThat(s.isSeasonBetsOpen()).isFalse();
        assertThat(s.getSeasonBetsStatusReason()).contains("No schedule generated yet");
        assertThat(s.isCanPlaceSeasonBets()).isFalse();
    }

    @Test
    void summaryRoundCountsAreCorrect() {
        Round r1 = round(1, RoundStatus.FINISHED);
        Round r2 = round(2, RoundStatus.OPEN_FOR_BETS);
        Round r3 = round(3, RoundStatus.NOT_STARTED);
        lenient().when(roundRepository.findAll()).thenReturn(List.of(r1, r2, r3));

        Match finished = finishedMatch();
        Match open = openBettingMatch();
        lenient().when(matchRepository.findAll()).thenReturn(List.of(finished, open));

        DashboardSummaryResponse s = dashboardService.getSummary("user@test.com");

        assertThat(s.getTotalRounds()).isEqualTo(3);
        assertThat(s.getFinishedRoundsCount()).isEqualTo(1);
        assertThat(s.getCurrentRoundNumber()).isEqualTo(2);
        assertThat(s.getNextOpenRoundNumber()).isEqualTo(2);
        assertThat(s.getNextRoundNumberToPlay()).isEqualTo(3);
        assertThat(s.getTotalMatches()).isEqualTo(2);
        assertThat(s.getFinishedMatchesCount()).isEqualTo(1);
        assertThat(s.isCanPlaceMatchBets()).isTrue();
    }

    @Test
    void adminFlagsSetForAdminUser() {
        user.setRole(Role.ADMIN);
        Team team = new Team();
        team.setId(1L);
        team.setName("Test");
        lenient().when(teamRepository.findAll()).thenReturn(List.of(team));
        // No rounds → schedule not generated
        lenient().when(roundRepository.findAll()).thenReturn(List.of());

        DashboardSummaryResponse s = dashboardService.getSummary("user@test.com");

        assertThat(s.isAdminCanGenerateSchedule()).isTrue();
        assertThat(s.isAdminCanOpenNextRound()).isFalse();
        assertThat(s.isAdminCanSimulateNextRound()).isFalse();
    }

    @Test
    void adminFlagsNotSetForRegularUser() {
        Team team = new Team();
        team.setId(1L);
        team.setName("Test");
        lenient().when(teamRepository.findAll()).thenReturn(List.of(team));

        DashboardSummaryResponse s = dashboardService.getSummary("user@test.com");

        assertThat(s.isAdminCanGenerateSchedule()).isFalse();
        assertThat(s.isAdminCanOpenNextRound()).isFalse();
        assertThat(s.isAdminCanSimulateNextRound()).isFalse();
    }

    @Test
    void hasOpenBetsTrueWhenOpenBetsExist() {
        lenient().when(betRepository.findByUser_IdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(openMatchBet("50.00", "2.00")));

        DashboardSummaryResponse s = dashboardService.getSummary("user@test.com");

        assertThat(s.isHasOpenBets()).isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Bet openMatchBet(String amount, String odds) {
        Team homeTeam = new Team(); homeTeam.setName("Home");
        Team awayTeam = new Team(); awayTeam.setName("Away");
        Match match = new Match();
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setStatus(MatchStatus.BETTING_OPEN);

        BigDecimal amt = new BigDecimal(amount);
        BigDecimal o = new BigDecimal(odds);

        Bet bet = new Bet();
        bet.setUser(user);
        bet.setBetType(BetType.SINGLE);
        bet.setMarket(BetMarket.MATCH_RESULT);
        bet.setMatch(match);
        bet.setPrediction(Prediction.HOME_WIN);
        bet.setAmount(amt);
        bet.setOdds(o);
        bet.setPossibleWin(amt.multiply(o));
        bet.setStatus(BetStatus.OPEN);
        return bet;
    }

    private Bet settledBet(BetStatus status, String amount, String profit) {
        Team home = new Team(); home.setName("H");
        Team away = new Team(); away.setName("A");
        Match m = new Match(); m.setHomeTeam(home); m.setAwayTeam(away);
        m.setStatus(MatchStatus.FINISHED);

        BigDecimal amt = new BigDecimal(amount);
        Bet bet = new Bet();
        bet.setUser(user);
        bet.setBetType(BetType.SINGLE);
        bet.setMarket(BetMarket.MATCH_RESULT);
        bet.setMatch(m);
        bet.setAmount(amt);
        bet.setOdds(new BigDecimal("2.00"));
        bet.setPossibleWin(amt.multiply(new BigDecimal("2.00")));
        bet.setStatus(status);
        bet.setProfit(new BigDecimal(profit));
        return bet;
    }

    private Bet cancelledBet(String amount) {
        Team home = new Team(); home.setName("H");
        Team away = new Team(); away.setName("A");
        Match m = new Match(); m.setHomeTeam(home); m.setAwayTeam(away);

        Bet bet = new Bet();
        bet.setUser(user);
        bet.setBetType(BetType.SINGLE);
        bet.setMarket(BetMarket.MATCH_RESULT);
        bet.setMatch(m);
        bet.setAmount(new BigDecimal(amount));
        bet.setOdds(new BigDecimal("2.00"));
        bet.setPossibleWin(new BigDecimal(amount).multiply(new BigDecimal("2.00")));
        bet.setStatus(BetStatus.CANCELLED);
        return bet;
    }

    private Round round(int number, RoundStatus status) {
        Round r = new Round();
        r.setRoundNumber(number);
        r.setStatus(status);
        return r;
    }

    private Match finishedMatch() {
        Match m = new Match();
        m.setStatus(MatchStatus.FINISHED);
        return m;
    }

    private Match openBettingMatch() {
        Match m = new Match();
        m.setStatus(MatchStatus.BETTING_OPEN);
        return m;
    }

    private Match scheduledMatch() {
        Match m = new Match();
        m.setStatus(MatchStatus.SCHEDULED);
        return m;
    }
}

package com.footballsim.service;

import com.footballsim.dto.AdminOverviewResponse;
import com.footballsim.entity.*;
import com.footballsim.enums.*;
import com.footballsim.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * BetService is a concrete class that Mockito cannot inline-mock on this JDK/OS combination,
 * so we use a real BetService instance (backed by mocked repositories) — same pattern as
 * BetServiceTest uses for OddsService.
 */
@ExtendWith(MockitoExtension.class)
class AdminOverviewTest {

    @Mock private TeamRepository teamRepository;
    @Mock private RoundRepository roundRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private BetRepository betRepository;
    @Mock private BetSelectionRepository betSelectionRepository;
    @Mock private UserRepository userRepository;
    @Mock private MatchEventRepository matchEventRepository;

    private OddsService oddsService;
    private BetService betService;
    private LeagueDataImportService leagueDataImportService;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        oddsService = new OddsService(playerRepository, teamRepository);
        betService = new BetService(betRepository, betSelectionRepository, matchRepository,
                userRepository, playerRepository, teamRepository, roundRepository, oddsService);
        leagueDataImportService = new LeagueDataImportService(teamRepository, playerRepository,
                roundRepository, matchRepository, betRepository, matchEventRepository);
        adminService = new AdminService(
                teamRepository, roundRepository, matchRepository,
                playerRepository, betRepository, betService, leagueDataImportService);

        lenient().when(betRepository.findAll()).thenReturn(List.of());
        // Default: no Round 1 → season bets closed
        lenient().when(roundRepository.findByRoundNumber(1)).thenReturn(Optional.empty());
    }

    @Test
    void noDataReturnsImportLeagueDataAction() {
        when(teamRepository.findAll()).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(List.of());
        when(roundRepository.findAll()).thenReturn(List.of());
        when(matchRepository.findAll()).thenReturn(List.of());

        AdminOverviewResponse r = adminService.getAdminOverview();

        assertThat(r.getTeamsCount()).isZero();
        assertThat(r.isScheduleGenerated()).isFalse();
        assertThat(r.getRecommendedActions()).anyMatch(a -> a.toLowerCase().contains("import"));
    }

    @Test
    void teamsButNoScheduleReturnsGenerateScheduleAction() {
        when(teamRepository.findAll()).thenReturn(List.of(team("Beer Sheva"), team("Haifa")));
        when(playerRepository.findAll()).thenReturn(List.of());
        when(roundRepository.findAll()).thenReturn(List.of());
        when(matchRepository.findAll()).thenReturn(List.of());

        AdminOverviewResponse r = adminService.getAdminOverview();

        assertThat(r.getTeamsCount()).isEqualTo(2);
        assertThat(r.isScheduleGenerated()).isFalse();
        assertThat(r.getRecommendedActions()).anyMatch(a -> a.toLowerCase().contains("generate schedule"));
    }

    @Test
    void scheduleGeneratedButRound1NotStartedReturnsOpenBettingAction() {
        when(teamRepository.findAll()).thenReturn(List.of(team("Beer Sheva")));
        when(playerRepository.findAll()).thenReturn(List.of());
        when(roundRepository.findAll()).thenReturn(List.of(round(1, RoundStatus.NOT_STARTED)));
        when(matchRepository.findAll()).thenReturn(List.of(scheduledMatch()));

        AdminOverviewResponse r = adminService.getAdminOverview();

        assertThat(r.isScheduleGenerated()).isTrue();
        assertThat(r.getNextRoundToOpen()).isEqualTo(1);
        assertThat(r.getRecommendedActions()).anyMatch(a -> a.contains("Open betting for Round 1"));
    }

    @Test
    void round1OpenReturnsSimulateAction() {
        when(teamRepository.findAll()).thenReturn(List.of(team("Beer Sheva")));
        when(playerRepository.findAll()).thenReturn(List.of());
        when(roundRepository.findAll()).thenReturn(List.of(round(1, RoundStatus.OPEN_FOR_BETS)));
        when(matchRepository.findAll()).thenReturn(List.of(openMatch()));

        AdminOverviewResponse r = adminService.getAdminOverview();

        assertThat(r.getNextRoundToSimulate()).isEqualTo(1);
        assertThat(r.getRecommendedActions()).anyMatch(a -> a.contains("Simulate Round 1"));
    }

    @Test
    void allRoundsFinishedReturnsSeasonCompleteMessage() {
        when(teamRepository.findAll()).thenReturn(List.of(team("Beer Sheva")));
        when(playerRepository.findAll()).thenReturn(List.of());
        when(roundRepository.findAll()).thenReturn(List.of(round(1, RoundStatus.FINISHED)));
        when(matchRepository.findAll()).thenReturn(List.of(finishedMatch()));

        AdminOverviewResponse r = adminService.getAdminOverview();

        assertThat(r.getNextRoundToOpen()).isNull();
        assertThat(r.getNextRoundToSimulate()).isNull();
        assertThat(r.getRecommendedActions()).anyMatch(a -> a.toLowerCase().contains("season complete"));
    }

    @Test
    void countsTeamsAndPlayersSeparately() {
        Team t1 = team("Haifa");
        t1.setId(1L);
        Team t2 = team("Jerusalem");
        t2.setId(2L);

        Player p1 = player(t1);
        Player p2 = player(t1);
        Player p3 = player(t2);

        when(teamRepository.findAll()).thenReturn(List.of(t1, t2));
        when(playerRepository.findAll()).thenReturn(List.of(p1, p2, p3));
        when(roundRepository.findAll()).thenReturn(List.of());
        when(matchRepository.findAll()).thenReturn(List.of());

        AdminOverviewResponse r = adminService.getAdminOverview();

        assertThat(r.getTeamsCount()).isEqualTo(2);
        assertThat(r.getPlayersCount()).isEqualTo(3);
    }

    @Test
    void completeSquadsCountTeamsWithAtLeast18Players() {
        Team t1 = team("BigSquad"); t1.setId(1L);
        Team t2 = team("SmallSquad"); t2.setId(2L);
        Team t3 = team("EmptySquad"); t3.setId(3L);

        // t1 gets 20 players (complete), t2 gets 5 (partial), t3 gets 0 (missing)
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < 20; i++) players.add(player(t1));
        for (int i = 0; i < 5; i++) players.add(player(t2));

        when(teamRepository.findAll()).thenReturn(List.of(t1, t2, t3));
        when(playerRepository.findAll()).thenReturn(players);
        when(roundRepository.findAll()).thenReturn(List.of());
        when(matchRepository.findAll()).thenReturn(List.of());

        AdminOverviewResponse r = adminService.getAdminOverview();

        assertThat(r.getCompleteSquadsCount()).isEqualTo(1); // only t1
        assertThat(r.getMissingSquadsCount()).isEqualTo(1);  // only t3
    }

    @Test
    void seasonBetsStatusIncludedInOverview() {
        // Configure Round 1 so isSeasonBettingOpen() returns true:
        //   round1 exists, is not FINISHED, and its matches are not FINISHED/IN_PROGRESS
        Round r1 = round(1, RoundStatus.OPEN_FOR_BETS);
        lenient().when(roundRepository.findByRoundNumber(1)).thenReturn(Optional.of(r1));
        lenient().when(matchRepository.findByRound_RoundNumberOrderByIdAsc(1))
                .thenReturn(List.of(openMatch()));

        when(teamRepository.findAll()).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(List.of());
        when(roundRepository.findAll()).thenReturn(List.of());
        when(matchRepository.findAll()).thenReturn(List.of());

        AdminOverviewResponse r = adminService.getAdminOverview();

        assertThat(r.isSeasonBetsOpen()).isTrue();
        assertThat(r.getSeasonBetsStatusReason()).contains("open");
    }

    @Test
    void seasonBetsLockedWhenNoRound1() {
        // Default setUp: findByRoundNumber(1) returns empty → isSeasonBettingOpen() = false
        when(teamRepository.findAll()).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(List.of());
        when(roundRepository.findAll()).thenReturn(List.of());
        when(matchRepository.findAll()).thenReturn(List.of());

        AdminOverviewResponse r = adminService.getAdminOverview();

        assertThat(r.isSeasonBetsOpen()).isFalse();
        assertThat(r.getSeasonBetsStatusReason()).contains("locked");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Team team(String name) {
        Team t = new Team();
        t.setName(name);
        return t;
    }

    private Round round(int number, RoundStatus status) {
        Round r = new Round();
        r.setRoundNumber(number);
        r.setStatus(status);
        return r;
    }

    private Match scheduledMatch() {
        Match m = new Match();
        m.setStatus(MatchStatus.SCHEDULED);
        return m;
    }

    private Match openMatch() {
        Match m = new Match();
        m.setStatus(MatchStatus.BETTING_OPEN);
        return m;
    }

    private Match finishedMatch() {
        Match m = new Match();
        m.setStatus(MatchStatus.FINISHED);
        return m;
    }

    private Player player(Team team) {
        Player p = new Player();
        p.setTeam(team);
        p.setFullName("Test Player");
        p.setPosition(Position.ST);
        p.setRating(70);
        p.setStatus(PlayerStatus.FIT);
        return p;
    }
}

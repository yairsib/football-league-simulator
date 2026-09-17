package com.footballsim.service;

import com.footballsim.dto.SeasonResetRequest;
import com.footballsim.dto.SeasonResetResponse;
import com.footballsim.entity.*;
import com.footballsim.enums.*;
import com.footballsim.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeasonResetServiceTest {

    @Mock private MatchEventRepository matchEventRepository;
    @Mock private BetSelectionRepository betSelectionRepository;
    @Mock private BetRepository betRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private RoundRepository roundRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private UserRepository userRepository;

    private SeasonResetService service;

    @BeforeEach
    void setUp() {
        service = new SeasonResetService(
                matchEventRepository, betSelectionRepository, betRepository,
                matchRepository, roundRepository, teamRepository, playerRepository,
                userRepository);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Persisted season baseline used by makeTeam — deliberately differs from the drifted current values. */
    private static final int BASELINE_SKILL = 85;
    private static final int BASELINE_MORALE = 6;

    private SeasonResetRequest confirmRequest() {
        SeasonResetRequest r = new SeasonResetRequest();
        r.setConfirm(true);
        return r;
    }

    private User makeUser(Long id, String balance) {
        User u = new User();
        u.setId(id);
        u.setBalance(new BigDecimal(balance));
        return u;
    }

    private Bet makeBet(User user, BetStatus status, String amount) {
        Bet b = new Bet();
        b.setUser(user);
        b.setStatus(status);
        b.setAmount(new BigDecimal(amount));
        return b;
    }

    /** A team whose skill/morale have drifted from its persisted baseline after a simulated season. */
    private Team makeTeam(Long id) {
        Team t = new Team();
        t.setId(id);
        t.setName("Team " + id);
        t.setSkillLevel(100);
        t.setMorale(10);
        t.setBaselineSkillLevel(BASELINE_SKILL);
        t.setBaselineMorale(BASELINE_MORALE);
        t.setPlayed(5); t.setWins(2); t.setDraws(1); t.setLosses(2);
        t.setGoalsFor(8); t.setGoalsAgainst(6); t.setPoints(7);
        t.setInjuries(1);
        return t;
    }

    private Player makePlayer(Long id, Team team) {
        Player p = new Player();
        p.setId(id);
        p.setTeam(team);
        p.setFullName("Player " + id);
        p.setPosition(Position.CM);
        p.setRating(75);
        p.setGoals(5); p.setAssists(3); p.setRedCards(1);
        p.setSuspensionMatchesRemaining(1);
        p.setStatus(PlayerStatus.INJURED);
        p.setInjuryDescription("Hamstring");
        p.setInjuredUntilRound(4);
        p.setInjuryMatchesRemaining(2);
        p.setInjuryMatchesTotal(3);
        return p;
    }

    private Round makeRound(int number) {
        Round r = new Round();
        r.setRoundNumber(number);
        r.setStatus(RoundStatus.FINISHED);
        return r;
    }

    private Match makeMatch(Team home, Team away) {
        Match m = new Match();
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        m.setStatus(MatchStatus.FINISHED);
        m.setHomeGoals(2);
        m.setAwayGoals(1);
        m.setBettingOpen(false);
        m.setHomeOdds(new BigDecimal("1.50"));
        m.setDrawOdds(new BigDecimal("4.00"));
        m.setAwayOdds(new BigDecimal("6.00"));
        m.setHandicapHomeMinusOneOdds(new BigDecimal("2.10"));
        m.setHandicapDrawOdds(new BigDecimal("3.90"));
        m.setHandicapAwayPlusOneOdds(new BigDecimal("1.60"));
        return m;
    }

    private void stubEmptySeasonWithTeams(Team... teams) {
        when(betRepository.findAll()).thenReturn(List.of());
        when(matchEventRepository.count()).thenReturn(0L);
        when(matchRepository.count()).thenReturn(0L);
        when(roundRepository.count()).thenReturn(0L);
        when(teamRepository.findAll()).thenReturn(List.of(teams));
        when(playerRepository.findAll()).thenReturn(List.of());
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    void confirmFalse_throwsIllegalArgumentException() {
        SeasonResetRequest req = new SeasonResetRequest(); // confirm defaults to false
        assertThatThrownBy(() -> service.resetSeason(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confirm=true");
    }

    @Test
    void emptyState_noCrash_allCountsZero() {
        stubEmptySeasonWithTeams();

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);
        SeasonResetResponse resp = service.resetSeason(req);

        assertThat(resp.getBetsDeleted()).isZero();
        assertThat(resp.getMatchEventsDeleted()).isZero();
        assertThat(resp.getTeamsReset()).isZero();
        assertThat(resp.getPlayersReset()).isZero();
        assertThat(resp.getOpenBetsRefunded()).isZero();
        assertThat(resp.isScheduleRegenerated()).isFalse();
    }

    @Test
    void openBets_areRefunded_beforeDeletion() {
        User alice = makeUser(1L, "500.00");
        User bob   = makeUser(2L, "300.00");

        Bet open1   = makeBet(alice, BetStatus.OPEN, "100.00");
        Bet open2   = makeBet(alice, BetStatus.OPEN,  "50.00");
        Bet open3   = makeBet(bob,   BetStatus.OPEN, "200.00");
        Bet settled = makeBet(alice, BetStatus.WON,  "100.00");

        when(betRepository.findAll()).thenReturn(List.of(open1, open2, open3, settled));
        when(matchEventRepository.count()).thenReturn(0L);
        when(matchRepository.count()).thenReturn(0L);
        when(roundRepository.count()).thenReturn(0L);
        when(teamRepository.findAll()).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);
        SeasonResetResponse resp = service.resetSeason(req);

        assertThat(resp.getOpenBetsRefunded()).isEqualTo(3);
        assertThat(alice.getBalance()).isEqualByComparingTo("650.00");
        assertThat(bob.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void matchEvents_areDeletedAndCounted() {
        when(betRepository.findAll()).thenReturn(List.of());
        when(matchEventRepository.count()).thenReturn(42L);
        when(matchRepository.count()).thenReturn(0L);
        when(roundRepository.count()).thenReturn(0L);
        when(teamRepository.findAll()).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(List.of());

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);
        SeasonResetResponse resp = service.resetSeason(req);

        assertThat(resp.getMatchEventsDeleted()).isEqualTo(42);
        verify(matchEventRepository).deleteAll();
    }

    @Test
    void allBetTypes_areDeleted() {
        User u = makeUser(1L, "900.00");
        List<Bet> bets = List.of(
                makeBet(u, BetStatus.WON,  "50"),
                makeBet(u, BetStatus.LOST, "25"),
                makeBet(u, BetStatus.OPEN, "30")
        );

        when(betRepository.findAll()).thenReturn(bets);
        when(matchEventRepository.count()).thenReturn(0L);
        when(matchRepository.count()).thenReturn(0L);
        when(roundRepository.count()).thenReturn(0L);
        when(teamRepository.findAll()).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(u));

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);
        SeasonResetResponse resp = service.resetSeason(req);

        assertThat(resp.getBetsDeleted()).isEqualTo(3);
        verify(betSelectionRepository).deleteAll();
        verify(betRepository).deleteAll();
    }

    @Test
    void teamStats_areZeroed_andSkillMoraleRestoredToPersistedBaseline() {
        Team t1 = makeTeam(1L);
        Team t2 = makeTeam(2L);
        stubEmptySeasonWithTeams(t1, t2);

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);
        SeasonResetResponse resp = service.resetSeason(req);

        assertThat(resp.getTeamsReset()).isEqualTo(2);
        for (Team t : List.of(t1, t2)) {
            assertThat(t.getPlayed()).isZero();
            assertThat(t.getWins()).isZero();
            assertThat(t.getDraws()).isZero();
            assertThat(t.getLosses()).isZero();
            assertThat(t.getGoalsFor()).isZero();
            assertThat(t.getGoalsAgainst()).isZero();
            assertThat(t.getPoints()).isZero();
            assertThat(t.getInjuries()).isZero();
            // Dynamic fields restored from the persisted baseline (not preserved from the drifted season)
            assertThat(t.getSkillLevel()).isEqualTo(BASELINE_SKILL);
            assertThat(t.getMorale()).isEqualTo(BASELINE_MORALE);
            // The baseline itself and identity are untouched
            assertThat(t.getBaselineSkillLevel()).isEqualTo(BASELINE_SKILL);
            assertThat(t.getBaselineMorale()).isEqualTo(BASELINE_MORALE);
            assertThat(t.getName()).startsWith("Team ");
        }
        assertThat(resp.getTeamBaselinesRestored()).isEqualTo(2);
    }

    @Test
    void playerStats_andInjuries_areZeroed() {
        Team t = makeTeam(1L);
        Player p = makePlayer(10L, t);

        when(betRepository.findAll()).thenReturn(List.of());
        when(matchEventRepository.count()).thenReturn(0L);
        when(matchRepository.count()).thenReturn(0L);
        when(roundRepository.count()).thenReturn(0L);
        when(teamRepository.findAll()).thenReturn(List.of(t));
        when(playerRepository.findAll()).thenReturn(List.of(p));

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);
        SeasonResetResponse resp = service.resetSeason(req);

        assertThat(resp.getPlayersReset()).isEqualTo(1);
        assertThat(p.getGoals()).isZero();
        assertThat(p.getAssists()).isZero();
        assertThat(p.getRedCards()).isZero();
        assertThat(p.getSuspensionMatchesRemaining()).isZero();
        assertThat(p.getStatus()).isEqualTo(PlayerStatus.FIT);
        assertThat(p.getInjuryDescription()).isNull();
        assertThat(p.getInjuredUntilRound()).isNull();
        assertThat(p.getInjuryMatchesRemaining()).isZero();
        assertThat(p.getInjuryMatchesTotal()).isEqualTo(0);
        assertThat(p.getFullName()).isEqualTo("Player 10");
        assertThat(p.getRating()).isEqualTo(75);
    }

    @Test
    void regenerateScheduleTrue_deletesOldAndRebuilds() {
        Team t1 = makeTeam(1L);
        Team t2 = makeTeam(2L);

        when(betRepository.findAll()).thenReturn(List.of());
        when(matchEventRepository.count()).thenReturn(0L);
        when(matchRepository.count()).thenReturn(6L);
        when(roundRepository.count()).thenReturn(2L);
        when(teamRepository.findAll()).thenReturn(List.of(t1, t2));
        when(playerRepository.findAll()).thenReturn(List.of());

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);
        SeasonResetResponse resp = service.resetSeason(req);

        verify(matchRepository).deleteAll();
        verify(roundRepository).deleteAll();
        verify(roundRepository, atLeastOnce()).save(any(Round.class));
        verify(matchRepository, atLeastOnce()).save(any(Match.class));

        assertThat(resp.getRoundsReset()).isEqualTo(2);
        assertThat(resp.getMatchesReset()).isEqualTo(6);
        assertThat(resp.isScheduleRegenerated()).isTrue();
    }

    @Test
    void regenerateScheduleFalse_resetsExistingFixtures_andClearsAllFrozenMarkets() {
        Team t1 = makeTeam(1L);
        Team t2 = makeTeam(2L);
        Round round = makeRound(1);
        Match match = makeMatch(t1, t2);

        when(betRepository.findAll()).thenReturn(List.of());
        when(matchEventRepository.count()).thenReturn(0L);
        when(matchRepository.findAll()).thenReturn(List.of(match));
        when(roundRepository.findAll()).thenReturn(List.of(round));
        when(teamRepository.findAll()).thenReturn(List.of(t1, t2));
        when(playerRepository.findAll()).thenReturn(List.of());

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(false);
        SeasonResetResponse resp = service.resetSeason(req);

        verify(matchRepository, never()).deleteAll();
        verify(roundRepository, never()).deleteAll();

        assertThat(match.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
        assertThat(match.getHomeGoals()).isNull();
        assertThat(match.getAwayGoals()).isNull();
        assertThat(match.isBettingOpen()).isFalse();
        assertThat(match.getHomeOdds()).isNull();
        assertThat(match.getDrawOdds()).isNull();
        assertThat(match.getAwayOdds()).isNull();
        assertThat(match.getHandicapHomeMinusOneOdds()).isNull();
        assertThat(match.getHandicapDrawOdds()).isNull();
        assertThat(match.getHandicapAwayPlusOneOdds()).isNull();

        assertThat(round.getStatus()).isEqualTo(RoundStatus.NOT_STARTED);
        assertThat(round.getStartedAt()).isNull();
        assertThat(round.getFinishedAt()).isNull();

        assertThat(resp.isScheduleRegenerated()).isFalse();
        assertThat(resp.getMatchesReset()).isEqualTo(1);
        assertThat(resp.getRoundsReset()).isEqualTo(1);
    }

    @Test
    void resetUserBalancesTrue_setsAllUsersTo1000() {
        User alice = makeUser(1L, "2500.00");
        User bob   = makeUser(2L,  "150.00");

        stubEmptySeasonWithTeams();
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);
        req.setResetUserBalances(true);
        SeasonResetResponse resp = service.resetSeason(req);

        assertThat(resp.getUsersBalanceReset()).isEqualTo(2);
        assertThat(alice.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(bob.getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void resetUserBalancesFalse_preservesBalancesAfterRefund() {
        User alice = makeUser(1L, "800.00");
        Bet openBet = makeBet(alice, BetStatus.OPEN, "200.00");

        when(betRepository.findAll()).thenReturn(List.of(openBet));
        when(matchEventRepository.count()).thenReturn(0L);
        when(matchRepository.count()).thenReturn(0L);
        when(roundRepository.count()).thenReturn(0L);
        when(teamRepository.findAll()).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(alice));

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);
        req.setResetUserBalances(false);
        SeasonResetResponse resp = service.resetSeason(req);

        assertThat(alice.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(resp.getUsersBalanceReset()).isZero();
    }

    @Test
    void resetUserBalancesTrue_supersedesToRefund() {
        User alice = makeUser(1L, "300.00");
        Bet openBet = makeBet(alice, BetStatus.OPEN, "100.00");

        when(betRepository.findAll()).thenReturn(List.of(openBet));
        when(matchEventRepository.count()).thenReturn(0L);
        when(matchRepository.count()).thenReturn(0L);
        when(roundRepository.count()).thenReturn(0L);
        when(teamRepository.findAll()).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(alice));

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);
        req.setResetUserBalances(true);
        SeasonResetResponse resp = service.resetSeason(req);

        assertThat(alice.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(resp.getUsersBalanceReset()).isEqualTo(1);
    }

    @Test
    void responseMessage_containsSummary() {
        stubEmptySeasonWithTeams();

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);
        SeasonResetResponse resp = service.resetSeason(req);

        assertThat(resp.getMessage()).isNotBlank();
        assertThat(resp.getMessage()).containsIgnoringCase("reset");
    }

    // ── Milestone 38/41: persisted season baseline ─────────────────────────────

    @Test
    void regenerateScheduleTrue_restoresSkillAndMoraleToPersistedBaseline() {
        Team t1 = makeTeam(1L);              // drifted to 100 / 10, baseline 85 / 6
        Team t2 = makeTeam(2L);
        t2.setSkillLevel(40);                // drifted the other way
        t2.setMorale(0);
        t2.setBaselineSkillLevel(63);        // this team's own randomised start
        t2.setBaselineMorale(5);
        stubEmptySeasonWithTeams(t1, t2);

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);
        SeasonResetResponse resp = service.resetSeason(req);

        assertThat(t1.getSkillLevel()).isEqualTo(85);
        assertThat(t1.getMorale()).isEqualTo(6);
        assertThat(t2.getSkillLevel()).isEqualTo(63);
        assertThat(t2.getMorale()).isEqualTo(5);
        assertThat(resp.getTeamBaselinesRestored()).isEqualTo(2);
        assertThat(resp.isScheduleRegenerated()).isTrue();
        assertThat(resp.getMessage()).contains("restored to baseline for 2 team(s)");
        verify(teamRepository).saveAll(List.of(t1, t2));
    }

    @Test
    void regenerateScheduleFalse_restoresSkillAndMoraleToPersistedBaseline() {
        Team t1 = makeTeam(1L);
        Team t2 = makeTeam(2L);
        t2.setSkillLevel(40);
        t2.setMorale(0);
        t2.setBaselineSkillLevel(63);
        t2.setBaselineMorale(5);

        Round round = makeRound(1);
        Match match = makeMatch(t1, t2);
        when(betRepository.findAll()).thenReturn(List.of());
        when(matchEventRepository.count()).thenReturn(0L);
        when(matchRepository.findAll()).thenReturn(List.of(match));
        when(roundRepository.findAll()).thenReturn(List.of(round));
        when(teamRepository.findAll()).thenReturn(List.of(t1, t2));
        when(playerRepository.findAll()).thenReturn(List.of());

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(false);
        SeasonResetResponse resp = service.resetSeason(req);

        assertThat(t1.getSkillLevel()).isEqualTo(85);
        assertThat(t1.getMorale()).isEqualTo(6);
        assertThat(t2.getSkillLevel()).isEqualTo(63);
        assertThat(t2.getMorale()).isEqualTo(5);
        assertThat(resp.getTeamBaselinesRestored()).isEqualTo(2);
        assertThat(resp.isScheduleRegenerated()).isFalse();
        assertThat(match.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
        verify(matchRepository, never()).deleteAll();
    }

    @Test
    void reset_neverRerollsBaseline_twoResetsGiveIdenticalValues() {
        Team t1 = makeTeam(1L);
        Team t2 = makeTeam(2L);
        t2.setBaselineSkillLevel(63);
        t2.setBaselineMorale(5);
        stubEmptySeasonWithTeams(t1, t2);

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);

        service.resetSeason(req);
        int s1 = t1.getSkillLevel(), s2 = t2.getSkillLevel();
        int m1 = t1.getMorale(),     m2 = t2.getMorale();

        // Drift again, then reset again — must land on exactly the same persisted values.
        t1.setSkillLevel(t1.getSkillLevel() + 4); t1.setMorale(9);
        t2.setSkillLevel(t2.getSkillLevel() - 6); t2.setMorale(1);
        service.resetSeason(req);

        assertThat(t1.getSkillLevel()).isEqualTo(s1).isEqualTo(BASELINE_SKILL);
        assertThat(t2.getSkillLevel()).isEqualTo(s2).isEqualTo(63);
        assertThat(t1.getMorale()).isEqualTo(m1).isEqualTo(BASELINE_MORALE);
        assertThat(t2.getMorale()).isEqualTo(m2).isEqualTo(5);
        assertThat(t1.getBaselineSkillLevel()).isEqualTo(BASELINE_SKILL);
        assertThat(t2.getBaselineSkillLevel()).isEqualTo(63);
    }

    @Test
    void teamWithoutPersistedBaseline_abortsBeforeAnyMutation() {
        Team t1 = makeTeam(1L);
        Team t2 = makeTeam(2L);
        Team t3 = makeTeam(3L);
        t2.setBaselineSkillLevel(null);   // never imported properly
        t3.setBaselineMorale(null);
        when(teamRepository.findAll()).thenReturn(List.of(t1, t2, t3));

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(false);
        assertThatThrownBy(() -> service.resetSeason(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Season reset aborted")
                .hasMessageContaining("Team 2")
                .hasMessageContaining("Team 3")
                .hasMessageContaining("No season data was modified");

        // Nothing was deleted, saved, refunded, or regenerated.
        verifyNoInteractions(matchEventRepository, betSelectionRepository, betRepository,
                matchRepository, roundRepository, playerRepository, userRepository);
        verify(teamRepository, never()).saveAll(any());
        verify(teamRepository, never()).save(any());
        for (Team t : List.of(t1, t2, t3)) {
            assertThat(t.getSkillLevel()).isEqualTo(100);
            assertThat(t.getMorale()).isEqualTo(10);
            assertThat(t.getPlayed()).isEqualTo(5);
            assertThat(t.getPoints()).isEqualTo(7);
        }
    }

    @Test
    void usersRolesAndPasswords_areNeverModifiedByReset() {
        Team t1 = makeTeam(1L);
        User admin = makeUser(1L, "1234.50");
        admin.setEmail("admin@test.com");
        admin.setUsername("admin");
        admin.setPasswordHash("hashed-password");
        admin.setRole(Role.ADMIN);
        stubEmptySeasonWithTeams(t1);

        SeasonResetRequest req = confirmRequest();
        req.setRegenerateSchedule(true);
        req.setResetUserBalances(false);
        service.resetSeason(req);

        // No open bets and no balance reset -> users are never even loaded, let alone changed.
        verifyNoInteractions(userRepository);
        assertThat(admin.getEmail()).isEqualTo("admin@test.com");
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getBalance()).isEqualByComparingTo("1234.50");
    }
}

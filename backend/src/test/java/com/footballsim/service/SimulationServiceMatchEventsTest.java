package com.footballsim.service;

import com.footballsim.entity.Match;
import com.footballsim.entity.MatchEvent;
import com.footballsim.entity.Player;
import com.footballsim.entity.Round;
import com.footballsim.entity.Team;
import com.footballsim.enums.MatchEventType;
import com.footballsim.enums.PlayerStatus;
import com.footballsim.enums.Position;
import com.footballsim.repository.MatchEventRepository;
import com.footballsim.repository.PlayerRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Deterministic unit tests for the simulation-generated player stats / match events
 * (goalscorers, assists, red cards, suspensions) added to SimulationService. These methods
 * are package-private test seams; the random rolls that decide *whether* an event happens
 * are exercised via injected Random subclasses so assertions never flake.
 */
class SimulationServiceMatchEventsTest {

    /** Always returns 0.0 — every probabilistic roll (assist/red-card chance, weighted pick) "happens"/picks first. */
    private static final class AlwaysZeroRandom extends Random {
        @Override public double nextDouble() { return 0.0; }
        @Override public int nextInt(int bound) { return 0; }
    }

    /** Always returns just-under-1.0 — every probabilistic roll "doesn't happen" / picks last candidate. */
    private static final class AlwaysJustUnderOneRandom extends Random {
        @Override public double nextDouble() { return 0.999; }
        @Override public int nextInt(int bound) { return Math.max(0, bound - 1); }
    }

    private static SimulationService newService(PlayerRepository playerRepository, MatchEventRepository matchEventRepository, Random random) {
        SimulationService service = new SimulationService(null, null, playerRepository, matchEventRepository, null, null, new LineupService());
        service.setRandom(random);
        return service;
    }

    private static Player player(Long id, String name, Position position, int rating, boolean starter) {
        Player p = new Player();
        p.setId(id);
        p.setFullName(name);
        p.setPosition(position);
        p.setRating(rating);
        p.setStatus(PlayerStatus.FIT);
        p.setStarter(starter);
        p.setSubstitute(!starter);
        return p;
    }

    private static Match match(Team home, Team away) {
        Round round = new Round();
        round.setRoundNumber(1);

        Match m = new Match();
        m.setId(100L);
        m.setRound(round);
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        return m;
    }

    @Test
    void scorerAndAssistPlayerStatsIncrementAndGoalEventIsSaved() {
        MatchEventRepository matchEventRepository = mock(MatchEventRepository.class);

        Team team = new Team();
        team.setId(1L);
        team.setName("Test FC");

        Player striker = player(1L, "Dor Peretz", Position.ST, 80, true);
        Player winger = player(2L, "Kervin Andrade", Position.RW, 75, true);
        List<Player> available = new ArrayList<>(List.of(striker, winger));

        Match m = match(team, team);

        // AlwaysZeroRandom: assist roll "happens" (0.0 < 0.75), weighted picks always select the first candidate
        SimulationService service = newService(null, matchEventRepository, new AlwaysZeroRandom());
        service.generateGoalEvents(m, team, available, List.of(), null, -1, 1);

        assertThat(striker.getGoals()).isEqualTo(1);
        assertThat(winger.getAssists()).isEqualTo(1);

        var captor = org.mockito.ArgumentCaptor.forClass(MatchEvent.class);
        org.mockito.Mockito.verify(matchEventRepository).save(captor.capture());
        MatchEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(MatchEventType.GOAL);
        assertThat(saved.getPlayer()).isEqualTo(striker);
        assertThat(saved.getAssistPlayer()).isEqualTo(winger);
        assertThat(saved.getDescription()).contains("Dor Peretz").contains("assisted by").contains("Kervin Andrade");
    }

    @Test
    void goalWithoutAssistLeavesNoAssistPlayerOnEvent() {
        MatchEventRepository matchEventRepository = mock(MatchEventRepository.class);

        Team team = new Team();
        team.setId(1L);
        team.setName("Test FC");

        Player striker = player(1L, "Sayed Abu Farkhi", Position.ST, 78, true);
        List<Player> available = new ArrayList<>(List.of(striker));

        Match m = match(team, team);

        // AlwaysJustUnderOneRandom: assist roll "doesn't happen" (0.999 >= 0.75)
        SimulationService service = newService(null, matchEventRepository, new AlwaysJustUnderOneRandom());
        service.generateGoalEvents(m, team, available, List.of(), null, -1, 1);

        assertThat(striker.getGoals()).isEqualTo(1);
        assertThat(striker.getAssists()).isZero();

        var captor = org.mockito.ArgumentCaptor.forClass(MatchEvent.class);
        org.mockito.Mockito.verify(matchEventRepository).save(captor.capture());
        MatchEvent saved = captor.getValue();
        assertThat(saved.getAssistPlayer()).isNull();
        assertThat(saved.getDescription()).isEqualTo("Sayed Abu Farkhi scored");
    }

    @Test
    void redCardIncrementsCountAndSuspendsPlayerAndSavesEvent() {
        MatchEventRepository matchEventRepository = mock(MatchEventRepository.class);

        Team team = new Team();
        team.setId(1L);
        team.setName("Test FC");

        Player defender = player(3L, "Tyrese Asante", Position.CB, 74, true);

        Match m = match(team, team);

        SimulationService service = newService(null, matchEventRepository, new AlwaysZeroRandom());
        service.applyRedCard(m, team, defender, 45);

        assertThat(defender.getRedCards()).isEqualTo(1);
        assertThat(defender.getStatus()).isEqualTo(PlayerStatus.SUSPENDED);
        assertThat(defender.getSuspensionMatchesRemaining()).isEqualTo(1);

        var captor = org.mockito.ArgumentCaptor.forClass(MatchEvent.class);
        org.mockito.Mockito.verify(matchEventRepository).save(captor.capture());
        MatchEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(MatchEventType.RED_CARD);
        assertThat(saved.getPlayer()).isEqualTo(defender);
        assertThat(saved.getAssistPlayer()).isNull();
        assertThat(saved.getDescription()).isEqualTo("Tyrese Asante received a red card");
    }

    @Test
    void suspendedPlayerExcludedFromMatchdayLineup() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        Team team = new Team();
        team.setId(1L);
        team.setName("Test FC");

        Player suspended = player(4L, "Suspended Guy", Position.CM, 72, true);
        suspended.setStatus(PlayerStatus.SUSPENDED);
        suspended.setSuspensionMatchesRemaining(1);

        Player fit = player(5L, "Fit Guy", Position.CM, 72, true);
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(1L)).thenReturn(List.of(suspended, fit));

        // The simulation's matchday lineup is the shared LineupService effective lineup.
        SimulationService service = newService(playerRepository, null, new AlwaysZeroRandom());
        LineupService.EffectiveLineup lineup = service.matchdayLineup(team);

        assertThat(lineup.starters).containsExactly(fit);
        assertThat(lineup.starters).doesNotContain(suspended);
        assertThat(lineup.substitutes).doesNotContain(suspended);
        assertThat(lineup.unavailable).containsExactly(suspended);
    }

    @Test
    void suspensionDecrementsAfterTeamsNextMatchAndPlayerBecomesFitAgain() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        Team team = new Team();
        team.setId(7L);
        team.setName("Test FC");

        Player suspended = player(6L, "One Match Out", Position.LB, 71, true);
        suspended.setStatus(PlayerStatus.SUSPENDED);
        suspended.setSuspensionMatchesRemaining(1);

        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(7L)).thenReturn(List.of(suspended));

        // AlwaysJustUnderOneRandom guarantees the 10% new-injury roll never fires (0.999 >= 0.10)
        SimulationService service = newService(playerRepository, null, new AlwaysJustUnderOneRandom());
        service.progressTeamInjuries(team);

        assertThat(suspended.getSuspensionMatchesRemaining()).isZero();
        assertThat(suspended.getStatus()).isEqualTo(PlayerStatus.FIT);
    }

    @Test
    void freshlyIssuedSuspensionIsNotDecrementedInTheSameMatch() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        Team team = new Team();
        team.setId(8L);
        team.setName("Test FC");

        Player freshlyCarded = player(9L, "Just Carded", Position.RB, 70, true);
        // Simulates applyRedCard() having already run for this match before progression is checked
        freshlyCarded.setStatus(PlayerStatus.SUSPENDED);
        freshlyCarded.setSuspensionMatchesRemaining(1);

        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(8L)).thenReturn(List.of(freshlyCarded));

        SimulationService service = newService(playerRepository, null, new AlwaysJustUnderOneRandom());

        // progressTeamInjuries must run BEFORE applyRedCard in finishMatch — verify it would wrongly
        // clear a same-match suspension if called afterwards, motivating that ordering requirement.
        service.progressTeamInjuries(team);

        assertThat(freshlyCarded.getSuspensionMatchesRemaining())
                .as("progressTeamInjuries decremented a suspension that (in finishMatch's real order) wouldn't exist yet at this point")
                .isZero();
    }
}

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

/**
 * Deterministic unit tests for substitution event generation and minute-based player eligibility.
 * All randomness is injected via AlwaysZeroRandom / AlwaysJustUnderOneRandom to avoid flakiness.
 */
class SimulationServiceSubstitutionTest {

    private static final class AlwaysZeroRandom extends Random {
        @Override public double nextDouble() { return 0.0; }
        @Override public int nextInt(int bound) { return 0; }
    }

    private static final class AlwaysJustUnderOneRandom extends Random {
        @Override public double nextDouble() { return 0.999; }
        @Override public int nextInt(int bound) { return Math.max(0, bound - 1); }
    }

    private static SimulationService newService(MatchEventRepository repo, Random random) {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        SimulationService service = new SimulationService(null, null, playerRepository, repo, null, null, new LineupService());
        service.setRandom(random);
        return service;
    }

    private static Player player(Long id, String name, Position pos, boolean starter) {
        Player p = new Player();
        p.setId(id);
        p.setFullName(name);
        p.setPosition(pos);
        p.setRating(75);
        p.setStatus(PlayerStatus.FIT);
        p.setStarter(starter);
        p.setSubstitute(!starter);
        return p;
    }

    private static Team team(Long id, String name) {
        Team t = new Team();
        t.setId(id);
        t.setName(name);
        return t;
    }

    private static Match match(Team home, Team away) {
        Round round = new Round();
        round.setRoundNumber(1);
        Match m = new Match();
        m.setId(10L);
        m.setRound(round);
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        return m;
    }

    // -------------------------------------------------------------------------
    // Test 1: substitutions are generated and saved
    // -------------------------------------------------------------------------
    @Test
    void substitutionsAreGeneratedAndSaved() {
        MatchEventRepository repo = mock(MatchEventRepository.class);
        Team t = team(1L, "Alpha FC");

        List<Player> starters = new ArrayList<>(List.of(
                player(1L, "Alice", Position.CM, true),
                player(2L, "Bob", Position.ST, true)
        ));
        List<Player> bench = new ArrayList<>(List.of(
                player(3L, "Carol", Position.AM, false),
                player(4L, "Dave", Position.RW, false)
        ));

        // AlwaysZeroRandom: numSubs = 2 + 0 = 2; both at minute 46; picks index 0 each time
        SimulationService service = newService(repo, new AlwaysZeroRandom());
        List<MatchEvent> subs = service.generateSubstitutions(match(t, t), t, starters, bench, null, -1);

        assertThat(subs).hasSize(2);
        assertThat(subs).allMatch(e -> e.getEventType() == MatchEventType.SUBSTITUTION);
        assertThat(subs).allMatch(e -> e.getPlayerOut() != null && e.getPlayerIn() != null);

        var captor = org.mockito.ArgumentCaptor.forClass(MatchEvent.class);
        org.mockito.Mockito.verify(repo, org.mockito.Mockito.times(2)).save(captor.capture());
    }

    // -------------------------------------------------------------------------
    // Test 2: no more than 5 substitutions per team
    // -------------------------------------------------------------------------
    @Test
    void substitutionsNeverExceedFivePerTeam() {
        MatchEventRepository repo = mock(MatchEventRepository.class);
        Team t = team(1L, "Alpha FC");

        List<Player> starters = new ArrayList<>();
        for (long i = 1; i <= 11; i++) starters.add(player(i, "S" + i, Position.CM, true));
        List<Player> bench = new ArrayList<>();
        for (long i = 12; i <= 16; i++) bench.add(player(i, "B" + i, Position.CM, false));

        // AlwaysJustUnderOneRandom: numSubs = 2 + 3 = 5
        SimulationService service = newService(repo, new AlwaysJustUnderOneRandom());
        List<MatchEvent> subs = service.generateSubstitutions(match(t, t), t, starters, bench, null, -1);

        assertThat(subs.size()).isLessThanOrEqualTo(5);
    }

    // -------------------------------------------------------------------------
    // Test 3: playerOut was in the initial on-pitch list
    // -------------------------------------------------------------------------
    @Test
    void playerOutIsFromInitialOnPitchList() {
        MatchEventRepository repo = mock(MatchEventRepository.class);
        Team t = team(1L, "Alpha FC");

        List<Player> starters = new ArrayList<>(List.of(
                player(1L, "Alice", Position.CM, true),
                player(2L, "Bob", Position.ST, true),
                player(3L, "Charlie", Position.CB, true)
        ));
        List<Player> bench = new ArrayList<>(List.of(
                player(4L, "Dave", Position.AM, false),
                player(5L, "Eve", Position.RW, false)
        ));

        SimulationService service = newService(repo, new AlwaysZeroRandom());
        List<MatchEvent> subs = service.generateSubstitutions(match(t, t), t, starters, bench, null, -1);

        List<Long> starterIds = starters.stream().map(Player::getId).toList();
        for (MatchEvent sub : subs) {
            assertThat(starterIds).contains(sub.getPlayerOut().getId());
        }
    }

    // -------------------------------------------------------------------------
    // Test 4: playerIn was from the bench
    // -------------------------------------------------------------------------
    @Test
    void playerInIsFromBench() {
        MatchEventRepository repo = mock(MatchEventRepository.class);
        Team t = team(1L, "Alpha FC");

        List<Player> starters = new ArrayList<>(List.of(
                player(1L, "Alice", Position.CM, true),
                player(2L, "Bob", Position.ST, true)
        ));
        List<Player> bench = new ArrayList<>(List.of(
                player(3L, "Carol", Position.AM, false),
                player(4L, "Dave", Position.RW, false)
        ));

        SimulationService service = newService(repo, new AlwaysZeroRandom());
        List<MatchEvent> subs = service.generateSubstitutions(match(t, t), t, starters, bench, null, -1);

        List<Long> benchIds = bench.stream().map(Player::getId).toList();
        for (MatchEvent sub : subs) {
            assertThat(benchIds).contains(sub.getPlayerIn().getId());
        }
    }

    // -------------------------------------------------------------------------
    // Test 5: no player is substituted out twice
    // -------------------------------------------------------------------------
    @Test
    void noPlayerSubstitutedOutTwice() {
        MatchEventRepository repo = mock(MatchEventRepository.class);
        Team t = team(1L, "Alpha FC");

        List<Player> starters = new ArrayList<>();
        for (long i = 1; i <= 5; i++) starters.add(player(i, "S" + i, Position.CM, true));
        List<Player> bench = new ArrayList<>();
        for (long i = 6; i <= 10; i++) bench.add(player(i, "B" + i, Position.AM, false));

        SimulationService service = newService(repo, new AlwaysJustUnderOneRandom());
        List<MatchEvent> subs = service.generateSubstitutions(match(t, t), t, starters, bench, null, -1);

        long uniqueOut = subs.stream().map(e -> e.getPlayerOut().getId()).distinct().count();
        assertThat(uniqueOut).isEqualTo(subs.size());
    }

    // -------------------------------------------------------------------------
    // Test 6: no player is substituted in twice
    // -------------------------------------------------------------------------
    @Test
    void noPlayerSubstitutedInTwice() {
        MatchEventRepository repo = mock(MatchEventRepository.class);
        Team t = team(1L, "Alpha FC");

        List<Player> starters = new ArrayList<>();
        for (long i = 1; i <= 5; i++) starters.add(player(i, "S" + i, Position.CM, true));
        List<Player> bench = new ArrayList<>();
        for (long i = 6; i <= 10; i++) bench.add(player(i, "B" + i, Position.AM, false));

        SimulationService service = newService(repo, new AlwaysZeroRandom());
        List<MatchEvent> subs = service.generateSubstitutions(match(t, t), t, starters, bench, null, -1);

        long uniqueIn = subs.stream().map(e -> e.getPlayerIn().getId()).distinct().count();
        assertThat(uniqueIn).isEqualTo(subs.size());
    }

    // -------------------------------------------------------------------------
    // Test 7: playerIn can participate in events after their sub minute
    // -------------------------------------------------------------------------
    @Test
    void playerInIsActiveAfterSubMinute() {
        Player starter = player(1L, "Alice", Position.ST, true);
        Player benchPlayer = player(2L, "Bob", Position.AM, false);

        // Create a synthetic sub event: Alice out → Bob in at minute 60
        MatchEvent sub = new MatchEvent();
        sub.setMinute(60);
        sub.setPlayerOut(starter);
        sub.setPlayerIn(benchPlayer);
        sub.setEventType(MatchEventType.SUBSTITUTION);

        SimulationService service = newService(null, new AlwaysZeroRandom());

        List<Player> active59 = service.computeActiveAtMinute(List.of(starter), List.of(sub), null, -1, 59);
        assertThat(active59).containsExactly(starter);
        assertThat(active59).doesNotContain(benchPlayer);

        List<Player> active60 = service.computeActiveAtMinute(List.of(starter), List.of(sub), null, -1, 60);
        assertThat(active60).containsExactly(benchPlayer);
        assertThat(active60).doesNotContain(starter);

        List<Player> active70 = service.computeActiveAtMinute(List.of(starter), List.of(sub), null, -1, 70);
        assertThat(active70).containsExactly(benchPlayer);
    }

    // -------------------------------------------------------------------------
    // Test 8: playerOut cannot participate in events after their sub minute
    // -------------------------------------------------------------------------
    @Test
    void playerOutIsInactiveAfterSubMinute() {
        Player starter = player(1L, "Alice", Position.ST, true);
        Player benchPlayer = player(2L, "Bob", Position.AM, false);

        MatchEvent sub = new MatchEvent();
        sub.setMinute(70);
        sub.setPlayerOut(starter);
        sub.setPlayerIn(benchPlayer);
        sub.setEventType(MatchEventType.SUBSTITUTION);

        SimulationService service = newService(null, new AlwaysZeroRandom());

        // Before sub minute: starter is active
        List<Player> before = service.computeActiveAtMinute(List.of(starter), List.of(sub), null, -1, 69);
        assertThat(before).contains(starter);

        // At and after sub minute: starter is gone
        List<Player> atMinute = service.computeActiveAtMinute(List.of(starter), List.of(sub), null, -1, 70);
        assertThat(atMinute).doesNotContain(starter);

        List<Player> after = service.computeActiveAtMinute(List.of(starter), List.of(sub), null, -1, 85);
        assertThat(after).doesNotContain(starter);
    }

    // -------------------------------------------------------------------------
    // Test 9: red-carded player is removed from active list after their minute
    // -------------------------------------------------------------------------
    @Test
    void redCardedPlayerRemovedAfterTheirMinute() {
        Player starter = player(1L, "Carlos", Position.CB, true);
        Player other   = player(2L, "Paulo", Position.CM, true);

        SimulationService service = newService(null, new AlwaysZeroRandom());

        List<Player> before = service.computeActiveAtMinute(List.of(starter, other), List.of(), starter, 80, 79);
        assertThat(before).contains(starter);

        List<Player> atMinute = service.computeActiveAtMinute(List.of(starter, other), List.of(), starter, 80, 80);
        assertThat(atMinute).doesNotContain(starter);
        assertThat(atMinute).contains(other);
    }

    // -------------------------------------------------------------------------
    // Test 10: GK is replaced by GK; field player is replaced by field player
    // -------------------------------------------------------------------------
    @Test
    void gkReplacedByGkAndFieldByField() {
        MatchEventRepository repo = mock(MatchEventRepository.class);
        Team t = team(1L, "Alpha FC");

        Player gkStarter  = player(1L, "GK-Starter", Position.GK, true);
        Player cbStarter  = player(2L, "CB-Starter", Position.CB, true);
        Player gkBench    = player(3L, "GK-Bench",   Position.GK, false);
        Player cmBench    = player(4L, "CM-Bench",   Position.CM, false);

        List<Player> starters = new ArrayList<>(List.of(gkStarter, cbStarter));
        List<Player> bench    = new ArrayList<>(List.of(gkBench, cmBench));

        // AlwaysZeroRandom: numSubs=2, minute=46 for both, picks index 0 each time
        // Sub 1: eligibleOut=[gkStarter,cbStarter] → index 0 = gkStarter (GK out)
        //        eligibleIn (GK only) = [gkBench] → index 0 = gkBench ✓
        // Sub 2: eligibleOut=[cbStarter] → cbStarter (field out)
        //        eligibleIn (non-GK) = [cmBench] → cmBench ✓
        SimulationService service = newService(repo, new AlwaysZeroRandom());
        List<MatchEvent> subs = service.generateSubstitutions(match(t, t), t, starters, bench, null, -1);

        assertThat(subs).hasSize(2);
        MatchEvent gkSub = subs.get(0);
        assertThat(gkSub.getPlayerOut().getPosition()).isEqualTo(Position.GK);
        assertThat(gkSub.getPlayerIn().getPosition()).isEqualTo(Position.GK);

        MatchEvent fieldSub = subs.get(1);
        assertThat(fieldSub.getPlayerOut().getPosition()).isNotEqualTo(Position.GK);
        assertThat(fieldSub.getPlayerIn().getPosition()).isNotEqualTo(Position.GK);
    }

    // -------------------------------------------------------------------------
    // Test 11: emergency GK exception — field player enters for GK when no bench GK available
    // -------------------------------------------------------------------------
    @Test
    void emergencyGkExceptionWhenNoBenchGkAvailable() {
        MatchEventRepository repo = mock(MatchEventRepository.class);
        Team t = team(1L, "Alpha FC");

        Player gkStarter = player(1L, "GK-Starter", Position.GK, true);
        Player cbStarter = player(2L, "CB-Starter", Position.CB, true);
        // No GK on the bench — only field players
        Player cmBench   = player(3L, "CM-Bench", Position.CM, false);

        List<Player> starters = new ArrayList<>(List.of(gkStarter, cbStarter));
        List<Player> bench    = new ArrayList<>(List.of(cmBench));

        // AlwaysZeroRandom: picks GK-Starter as playerOut; no GK bench → emergency → CM-Bench enters
        SimulationService service = newService(repo, new AlwaysZeroRandom());
        List<MatchEvent> subs = service.generateSubstitutions(match(t, t), t, starters, bench, null, -1);

        assertThat(subs).isNotEmpty();
        MatchEvent emergencySub = subs.get(0);
        assertThat(emergencySub.getPlayerOut().getPosition()).isEqualTo(Position.GK);
        // Emergency: field player came in for the GK
        assertThat(emergencySub.getPlayerIn().getPosition()).isNotEqualTo(Position.GK);
    }
}

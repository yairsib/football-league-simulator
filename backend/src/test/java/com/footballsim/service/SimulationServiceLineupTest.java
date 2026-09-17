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
 * Milestone 41 regression tests: (a) the simulation fields exactly the effective lineup that the
 * Lineups endpoint shows (one source of truth via LineupService), and (b) the red-card /
 * substitution timeline is always logically valid.
 */
class SimulationServiceLineupTest {

    /** Every roll "happens" and every pick is index 0 — red card fires and goes to the first starter. */
    private static final class AlwaysZeroRandom extends Random {
        @Override public double nextDouble() { return 0.0; }
        @Override public int nextInt(int bound) { return 0; }
    }

    /** Red card roll never fires (0.999 >= 0.08); picks are the last candidate. */
    private static final class AlwaysJustUnderOneRandom extends Random {
        @Override public double nextDouble() { return 0.999; }
        @Override public int nextInt(int bound) { return Math.max(0, bound - 1); }
    }

    private final LineupService lineupService = new LineupService();

    private SimulationService newService(PlayerRepository playerRepository, MatchEventRepository eventRepo, Random random) {
        SimulationService service = new SimulationService(null, null, playerRepository, eventRepo, null, null, lineupService);
        service.setRandom(random);
        return service;
    }

    private static Player player(long id, String name, Position pos, int rating, boolean starter, boolean substitute) {
        Player p = new Player();
        p.setId(id);
        p.setFullName(name);
        p.setPosition(pos);
        p.setRating(rating);
        p.setStatus(PlayerStatus.FIT);
        p.setStarter(starter);
        p.setSubstitute(substitute);
        return p;
    }

    private static Team team(long id) {
        Team t = new Team();
        t.setId(id);
        t.setName("Team " + id);
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

    /** A realistic squad: 11 flagged starters (1 GK), 7 flagged subs (1 GK), 3 extra squad players. */
    private static List<Player> squad() {
        List<Player> squad = new ArrayList<>();
        squad.add(player(1, "GK-S", Position.GK, 80, true, false));
        Position[] outfield = {Position.RB, Position.CB, Position.CB, Position.LB, Position.DM,
                Position.CM, Position.CM, Position.AM, Position.RW, Position.ST};
        for (int i = 0; i < outfield.length; i++) {
            squad.add(player(2 + i, "S" + (2 + i), outfield[i], 78, true, false));
        }
        squad.add(player(12, "GK-B", Position.GK, 70, false, true));
        Position[] bench = {Position.CB, Position.LB, Position.CM, Position.AM, Position.LW, Position.ST};
        for (int i = 0; i < bench.length; i++) {
            squad.add(player(13 + i, "B" + (13 + i), bench[i], 72, false, true));
        }
        squad.add(player(19, "X19", Position.CM, 65, false, false));
        squad.add(player(20, "X20", Position.ST, 64, false, false));
        squad.add(player(21, "X21", Position.CB, 63, false, false));
        return squad;
    }

    private static void injure(Player p) {
        p.setStatus(PlayerStatus.INJURED);
        p.setInjuryMatchesRemaining(3);
        p.setInjuryMatchesTotal(3);
    }

    // ── (a) one source of truth for the matchday lineup ───────────────────────

    @Test
    void simulationStartingXI_equalsLineupsEndpointEffectiveXI_forInjuredStarter() {
        Team team = team(1L);
        List<Player> squad = squad();
        Player injuredStarter = squad.get(5);   // S6 (DM), flagged starter
        injure(injuredStarter);

        PlayerRepository playerRepository = mock(PlayerRepository.class);
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(1L)).thenReturn(squad);

        // What PlayerService.buildLineup shows to the user
        LineupService.EffectiveLineup displayed = lineupService.generateEffectiveLineup(squad);
        // What the simulation fields
        LineupService.EffectiveLineup simulated = newService(playerRepository, null, new AlwaysJustUnderOneRandom()).matchdayLineup(team);

        List<Long> displayedIds = displayed.starters.stream().map(Player::getId).toList();
        List<Long> simulatedIds = simulated.starters.stream().map(Player::getId).toList();
        assertThat(simulatedIds).containsExactlyElementsOf(displayedIds);
        assertThat(simulated.substitutes.stream().map(Player::getId).toList())
                .containsExactlyElementsOf(displayed.substitutes.stream().map(Player::getId).toList());

        // Exactly 11 start, the injured starter is out, and a replacement stepped in
        assertThat(simulated.starters).hasSize(11);
        assertThat(simulated.starters).doesNotContain(injuredStarter);
        assertThat(simulated.substitutes).doesNotContain(injuredStarter);
        assertThat(simulated.replacements).hasSize(1);
        assertThat(simulated.replacements.get(0).originalPlayerName).isEqualTo(injuredStarter.getFullName());
    }

    @Test
    void injuredStarterCannotParticipate_replacementCanScore_andSubsComeFromEffectiveBench() {
        Team team = team(1L);
        List<Player> squad = squad();
        Player injuredStarter = squad.get(10);  // S11 (ST)
        injure(injuredStarter);

        LineupService.EffectiveLineup lineup = lineupService.generateEffectiveLineup(squad);
        Player replacement = lineup.starters.stream()
                .filter(p -> !p.isStarter())
                .findFirst().orElseThrow();

        MatchEventRepository eventRepo = mock(MatchEventRepository.class);
        SimulationService service = newService(null, eventRepo, new AlwaysJustUnderOneRandom());

        List<Player> starters = new ArrayList<>(lineup.starters);
        List<Player> bench = new ArrayList<>(lineup.substitutes);
        List<MatchEvent> subs = service.generateSubstitutions(match(team, team), team, starters, bench, null, -1);

        // every incoming player is from the effective bench, and nobody injured ever appears
        List<Long> benchIds = bench.stream().map(Player::getId).toList();
        assertThat(subs).isNotEmpty();
        for (MatchEvent sub : subs) {
            assertThat(benchIds).contains(sub.getPlayerIn().getId());
            assertThat(sub.getPlayerIn()).isNotEqualTo(injuredStarter);
            assertThat(sub.getPlayerOut()).isNotEqualTo(injuredStarter);
        }

        // the replacement is on the pitch from minute 1 and can be the scorer
        List<Player> activeAtOne = service.computeActiveAtMinute(starters, subs, null, -1, 1);
        assertThat(activeAtOne).contains(replacement).doesNotContain(injuredStarter);

        // With 2 goals: minute = 1 + nextInt(90) = 90 for AlwaysJustUnderOne; scorer = last active by cumulative weight
        service.generateGoalEvents(match(team, team), team, starters, subs, null, -1, 2);
        assertThat(injuredStarter.getGoals()).isZero();
        assertThat(injuredStarter.getAssists()).isZero();
    }

    @Test
    void goalkeeperRulesStillHoldWithEffectiveLineup() {
        Team team = team(1L);
        List<Player> squad = squad();
        injure(squad.get(0)); // starting GK injured -> bench GK must be promoted

        LineupService.EffectiveLineup lineup = lineupService.generateEffectiveLineup(squad);
        long gksInXI = lineup.starters.stream().filter(p -> p.getPosition() == Position.GK).count();
        assertThat(gksInXI).isEqualTo(1);
        assertThat(lineup.starters).noneMatch(p -> p.getId() == 1L);

        MatchEventRepository eventRepo = mock(MatchEventRepository.class);
        SimulationService service = newService(null, eventRepo, new AlwaysZeroRandom());
        List<MatchEvent> subs = service.generateSubstitutions(match(team, team), team,
                new ArrayList<>(lineup.starters), new ArrayList<>(lineup.substitutes), null, -1);
        // No bench GK remains, so a GK can only ever be replaced through the emergency rule; an
        // outfield player is never replaced by a GK.
        for (MatchEvent sub : subs) {
            if (sub.getPlayerOut().getPosition() != Position.GK) {
                assertThat(sub.getPlayerIn().getPosition()).isNotEqualTo(Position.GK);
            }
        }
    }

    // ── (b) red-card / substitution timeline consistency ──────────────────────

    @Test
    void redCardCandidateIsAlwaysAMemberOfTheStartingXI() {
        List<Player> squad = squad();
        LineupService.EffectiveLineup lineup = lineupService.generateEffectiveLineup(squad);

        SimulationService service = newService(null, null, new AlwaysZeroRandom());
        Player carded = service.maybeSelectRedCardPlayer(lineup.starters);

        assertThat(carded).isNotNull();
        assertThat(lineup.starters).contains(carded);
        assertThat(lineup.substitutes).doesNotContain(carded);

        // roll "doesn't happen" -> no card
        assertThat(newService(null, null, new AlwaysJustUnderOneRandom()).maybeSelectRedCardPlayer(lineup.starters)).isNull();
        // nobody on the pitch -> no card
        assertThat(service.maybeSelectRedCardPlayer(List.of())).isNull();
    }

    @Test
    void redCardedPlayer_isNeverSubstitutedOff_beforeTheCard_andNoSyntheticSubIsCreated() {
        Team team = team(1L);
        List<Player> squad = squad();
        LineupService.EffectiveLineup lineup = lineupService.generateEffectiveLineup(squad);
        MatchEventRepository eventRepo = mock(MatchEventRepository.class);

        // AlwaysZeroRandom would pick index 0 = the first starter as playerOut at minute 46 for both subs.
        // Make that exact player the red-carded one, with a LATE card (88') — the old bug scenario.
        Player carded = lineup.starters.get(0);
        int cardMinute = 88;

        SimulationService service = newService(null, eventRepo, new AlwaysZeroRandom());
        List<MatchEvent> subs = service.generateSubstitutions(match(team, team), team,
                new ArrayList<>(lineup.starters), new ArrayList<>(lineup.substitutes), carded, cardMinute);

        // (1) no substitution-off event exists for the carded player, at any minute
        assertThat(subs).noneMatch(e -> e.getPlayerOut() == carded);
        assertThat(subs).noneMatch(e -> e.getPlayerIn() == carded);
        // (2) substitutions still happen normally (2 with AlwaysZeroRandom) — no synthetic one for the card
        assertThat(subs).hasSize(2);
        assertThat(subs).allMatch(e -> e.getEventType() == MatchEventType.SUBSTITUTION);
        // (3) same substitution count as a match without a red card -> nothing was synthesised
        List<MatchEvent> subsNoCard = newService(null, mock(MatchEventRepository.class), new AlwaysZeroRandom())
                .generateSubstitutions(match(team, team), team,
                        new ArrayList<>(lineup.starters), new ArrayList<>(lineup.substitutes), null, -1);
        assertThat(subs).hasSameSizeAs(subsNoCard);

        // (4) the carded player is active before the card minute, and gone at/after it
        List<Player> at87 = service.computeActiveAtMinute(lineup.starters, subs, carded, cardMinute, 87);
        List<Player> at88 = service.computeActiveAtMinute(lineup.starters, subs, carded, cardMinute, 88);
        List<Player> at90 = service.computeActiveAtMinute(lineup.starters, subs, carded, cardMinute, 90);
        assertThat(at87).contains(carded);
        assertThat(at88).doesNotContain(carded);
        assertThat(at90).doesNotContain(carded);
        // (5) the team plays on with one fewer after the card (no replacement enters for them)
        assertThat(at88).hasSize(at87.size() - 1);
    }

    @Test
    void redCardedPlayer_cannotBeSubstitutedAfterTheCard_andSubsStillValid() {
        Team team = team(1L);
        List<Player> squad = squad();
        LineupService.EffectiveLineup lineup = lineupService.generateEffectiveLineup(squad);
        MatchEventRepository eventRepo = mock(MatchEventRepository.class);

        // Early card (minute 10), subs at 46 (AlwaysZeroRandom): the carded player must already be
        // off the pitch and can never be chosen as playerOut.
        Player carded = lineup.starters.get(0);
        SimulationService service = newService(null, eventRepo, new AlwaysZeroRandom());
        List<MatchEvent> subs = service.generateSubstitutions(match(team, team), team,
                new ArrayList<>(lineup.starters), new ArrayList<>(lineup.substitutes), carded, 10);

        assertThat(subs).hasSize(2);
        assertThat(subs).noneMatch(e -> e.getPlayerOut() == carded || e.getPlayerIn() == carded);
        for (MatchEvent sub : subs) {
            assertThat(sub.getMinute()).isBetween(46, 85);
            assertThat(lineup.starters).contains(sub.getPlayerOut());
            assertThat(lineup.substitutes).contains(sub.getPlayerIn());
            // every substitution-off happens AFTER the card, and never for the carded player
            assertThat(sub.getMinute()).isGreaterThan(10);
        }

        // and the carded player cannot score/assist after the card minute
        assertThat(service.computeActiveAtMinute(lineup.starters, subs, carded, 10, 9)).contains(carded);
        assertThat(service.computeActiveAtMinute(lineup.starters, subs, carded, 10, 10)).doesNotContain(carded);
        assertThat(service.computeActiveAtMinute(lineup.starters, subs, carded, 10, 60)).doesNotContain(carded);
    }
}

package com.footballsim.service;

import com.footballsim.entity.Player;
import com.footballsim.enums.PlayerStatus;
import com.footballsim.enums.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineupServiceTest {

    private final LineupService service = new LineupService();

    private static Player player(long id, String name, Position position, int rating, boolean starter, boolean substitute) {
        Player p = new Player();
        p.setId(id);
        p.setFullName(name);
        p.setPosition(position);
        p.setRating(rating);
        p.setStatus(PlayerStatus.FIT);
        p.setStarter(starter);
        p.setSubstitute(substitute);
        return p;
    }

    private static void injure(Player p, int matchesRemaining) {
        p.setStatus(PlayerStatus.INJURED);
        p.setInjuryMatchesRemaining(matchesRemaining);
        p.setInjuryMatchesTotal(matchesRemaining);
    }

    private static void suspend(Player p, int matchesRemaining) {
        p.setStatus(PlayerStatus.SUSPENDED);
        p.setSuspensionMatchesRemaining(matchesRemaining);
    }

    /** A standard 11 starters (4-3-3-ish) + 9 substitutes + a few additional squad players, all FIT. */
    private static List<Player> buildFullSquad() {
        List<Player> squad = new ArrayList<>();
        long id = 1;

        Position[] starterPositions = {
                Position.GK, Position.RB, Position.CB, Position.CB, Position.LB,
                Position.DM, Position.CM, Position.AM, Position.RW, Position.ST, Position.LW
        };
        for (Position pos : starterPositions) {
            squad.add(player(id, "Starter-" + pos + "-" + id, pos, 80, true, false));
            id++;
        }

        Position[] substitutePositions = {
                Position.GK, Position.CB, Position.RB, Position.LB, Position.DM,
                Position.CM, Position.AM, Position.ST, Position.LW
        };
        for (Position pos : substitutePositions) {
            squad.add(player(id, "Sub-" + pos + "-" + id, pos, 72, false, true));
            id++;
        }

        Position[] extraPositions = {Position.CB, Position.ST, Position.CM};
        for (Position pos : extraPositions) {
            squad.add(player(id, "Extra-" + pos + "-" + id, pos, 65, false, false));
            id++;
        }

        return squad;
    }

    private static Player findByName(List<Player> players, String namePart) {
        return players.stream().filter(p -> p.getFullName().contains(namePart)).findFirst().orElse(null);
    }

    // A. Injured starter ST: replacement chosen from available ST substitute, unavailable player excluded, 11 starters kept
    @Test
    void injuredStarterStriker_isReplacedBySubstituteStriker() {
        List<Player> squad = buildFullSquad();
        Player starterSt = findByName(squad, "Starter-ST");
        injure(starterSt, 3);

        LineupService.EffectiveLineup lineup = service.generateEffectiveLineup(squad);

        assertEquals(11, lineup.starters.size());
        assertFalse(lineup.starters.contains(starterSt));
        assertTrue(lineup.unavailable.contains(starterSt));
        assertFalse(lineup.substitutes.contains(starterSt));

        Player subSt = findByName(squad, "Sub-ST");
        assertTrue(lineup.starters.contains(subSt), "substitute striker should be promoted to starter");

        assertEquals(1, lineup.replacements.size());
        assertEquals(starterSt.getFullName(), lineup.replacements.get(0).originalPlayerName);
        assertEquals(subSt.getFullName(), lineup.replacements.get(0).replacementPlayerName);
        assertEquals("Injured", lineup.replacements.get(0).reason);
    }

    // B. Suspended starting GK: replacement chosen from backup GK, exactly one starting GK remains
    @Test
    void suspendedStartingGoalkeeper_isReplacedByBackupGoalkeeper() {
        List<Player> squad = buildFullSquad();
        Player starterGk = findByName(squad, "Starter-GK");
        suspend(starterGk, 1);

        LineupService.EffectiveLineup lineup = service.generateEffectiveLineup(squad);

        long gkCount = lineup.starters.stream().filter(p -> p.getPosition() == Position.GK).count();
        assertEquals(1, gkCount, "exactly one starting goalkeeper should remain");
        assertFalse(lineup.starters.contains(starterGk));
        assertTrue(lineup.unavailable.contains(starterGk));

        Player subGk = findByName(squad, "Sub-GK");
        assertTrue(lineup.starters.contains(subGk), "backup goalkeeper should start instead");
        assertEquals(11, lineup.starters.size());
    }

    // C. Injured CB with no CB substitute: replacement chosen from compatible RB/LB/DM, 11 starters kept
    @Test
    void injuredCenterBack_withNoCbSubstitute_isReplacedByCompatiblePosition() {
        List<Player> squad = buildFullSquad();
        // Remove every CB from the bench/extras so no direct CB replacement exists.
        squad.removeIf(p -> p.getPosition() == Position.CB && !p.isStarter());

        Player starterCb = squad.stream()
                .filter(p -> p.isStarter() && p.getPosition() == Position.CB)
                .findFirst().orElseThrow();
        injure(starterCb, 2);

        LineupService.EffectiveLineup lineup = service.generateEffectiveLineup(squad);

        assertFalse(lineup.starters.contains(starterCb));
        assertEquals(11, lineup.starters.size());
        assertEquals(1, lineup.replacements.size());

        Position replacementPosition = squad.stream()
                .filter(p -> p.getFullName().equals(lineup.replacements.get(0).replacementPlayerName))
                .findFirst().orElseThrow().getPosition();
        assertTrue(Set.of(Position.RB, Position.LB, Position.DM).contains(replacementPosition),
                "replacement should come from a compatible nearby position: " + replacementPosition);
    }

    // D. Multiple unavailable starters: no duplicate replacement players, 11 starters kept
    @Test
    void multipleUnavailableStarters_produceNoDuplicateReplacements() {
        List<Player> squad = buildFullSquad();
        Player starterSt = findByName(squad, "Starter-ST");
        Player starterCb = squad.stream().filter(p -> p.isStarter() && p.getPosition() == Position.CB).findFirst().orElseThrow();
        injure(starterSt, 2);
        suspend(starterCb, 1);

        LineupService.EffectiveLineup lineup = service.generateEffectiveLineup(squad);

        assertEquals(11, lineup.starters.size());
        assertEquals(2, lineup.replacements.size());

        List<String> replacementNames = lineup.replacements.stream().map(r -> r.replacementPlayerName).toList();
        assertEquals(replacementNames.size(), replacementNames.stream().distinct().count(),
                "replacement players must be distinct");

        Set<Long> starterIds = lineup.starters.stream().map(Player::getId).collect(Collectors.toSet());
        assertEquals(starterIds.size(), lineup.starters.size(), "no player should start twice");
    }

    // E. Unavailable substitute: removed from bench, bench refilled from additional squad player
    @Test
    void unavailableSubstitute_isRemovedFromBenchAndRefilledFromAdditionalSquad() {
        List<Player> squad = buildFullSquad();
        Player subCm = findByName(squad, "Sub-CM");
        injure(subCm, 4);

        LineupService.EffectiveLineup lineup = service.generateEffectiveLineup(squad);

        assertFalse(lineup.substitutes.contains(subCm));
        assertTrue(lineup.unavailable.contains(subCm));

        Player extraCm = findByName(squad, "Extra-CM");
        assertTrue(lineup.substitutes.contains(extraCm), "additional squad player should refill the bench");
    }

    // F. Team with fewer than 11 available players: no crash, fewer starters returned, warning present
    @Test
    void fewerThanElevenAvailablePlayers_returnsAsManyAsPossibleWithWarning() {
        List<Player> squad = buildFullSquad();
        // Knock out enough players that fewer than 11 remain available overall.
        int toDisable = squad.size() - 9;
        for (int i = 0; i < toDisable; i++) {
            injure(squad.get(i), 1);
        }

        LineupService.EffectiveLineup lineup = service.generateEffectiveLineup(squad);

        assertTrue(lineup.starters.size() <= 9);
        assertFalse(lineup.warnings.isEmpty(), "a warning should be present when fewer than 11 players are available");
        assertTrue(lineup.starters.stream().noneMatch(service::isUnavailable));
        assertTrue(lineup.substitutes.stream().noneMatch(service::isUnavailable));
    }

    @Test
    void fullyAvailableSquad_keepsOriginalStartersAndReportsNoChanges() {
        List<Player> squad = buildFullSquad();

        LineupService.EffectiveLineup lineup = service.generateEffectiveLineup(squad);

        assertEquals(11, lineup.starters.size());
        assertTrue(lineup.starters.stream().allMatch(Player::isStarter));
        assertTrue(lineup.replacements.isEmpty());
        assertFalse(lineup.effectiveLineupGenerated);
        assertTrue(lineup.unavailable.isEmpty());
        assertNull(findByName(lineup.starters, "DOES_NOT_EXIST"));
    }
}

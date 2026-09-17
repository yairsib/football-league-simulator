package com.footballsim.service;

import com.footballsim.entity.Player;
import com.footballsim.entity.Team;
import com.footballsim.enums.PlayerStatus;
import com.footballsim.enums.Position;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates an effective lineup (starters/substitutes/unavailable + replacements) for a team
 * at request time, without rewriting the persisted starter/substitute roles. The original squad
 * roles remain the baseline; unavailable players (injured with matches remaining, or suspended)
 * are swapped out for the best-fitting available replacement by position compatibility and rating.
 */
@Service
public class LineupService {

    private static final int TARGET_STARTERS = 11;
    private static final int TARGET_SUBSTITUTES = 7;

    /** Preferred replacement positions, in priority order, for each missing starter position (excluding the position itself). */
    private static final Map<Position, List<Position>> COMPATIBLE_POSITIONS = buildCompatibilityMap();

    private static Map<Position, List<Position>> buildCompatibilityMap() {
        Map<Position, List<Position>> map = new EnumMap<>(Position.class);
        map.put(Position.GK, List.of());
        map.put(Position.CB, List.of(Position.RB, Position.LB, Position.DM));
        map.put(Position.RB, List.of(Position.CB, Position.LB, Position.DM));
        map.put(Position.LB, List.of(Position.CB, Position.RB, Position.DM));
        map.put(Position.DM, List.of(Position.CM, Position.CB));
        map.put(Position.CM, List.of(Position.DM, Position.AM));
        map.put(Position.AM, List.of(Position.CM, Position.LW, Position.RW));
        map.put(Position.LW, List.of(Position.RW, Position.AM, Position.ST));
        map.put(Position.RW, List.of(Position.LW, Position.AM, Position.ST));
        map.put(Position.ST, List.of(Position.LW, Position.RW, Position.AM));
        return map;
    }

    /** Result of generating an effective lineup for one team. */
    public static class EffectiveLineup {
        public final List<Player> starters;
        public final List<Player> substitutes;
        public final List<Player> unavailable;
        public final List<Replacement> replacements;
        public final List<String> warnings;
        public final boolean effectiveLineupGenerated;

        EffectiveLineup(List<Player> starters, List<Player> substitutes, List<Player> unavailable,
                        List<Replacement> replacements, List<String> warnings, boolean effectiveLineupGenerated) {
            this.starters = starters;
            this.substitutes = substitutes;
            this.unavailable = unavailable;
            this.replacements = replacements;
            this.warnings = warnings;
            this.effectiveLineupGenerated = effectiveLineupGenerated;
        }
    }

    /** A single starter replacement: who was unavailable, who replaced them, why, and for which position. */
    public static class Replacement {
        public final String originalPlayerName;
        public final String replacementPlayerName;
        public final String reason;
        public final Position position;

        Replacement(String originalPlayerName, String replacementPlayerName, String reason, Position position) {
            this.originalPlayerName = originalPlayerName;
            this.replacementPlayerName = replacementPlayerName;
            this.reason = reason;
            this.position = position;
        }
    }

    public boolean isUnavailable(Player player) {
        return player.getStatus() == PlayerStatus.SUSPENDED && player.getSuspensionMatchesRemaining() > 0
                || (player.getStatus() == PlayerStatus.INJURED && player.getInjuryMatchesRemaining() > 0);
    }

    /**
     * Builds the effective lineup for a team's squad:
     * - removes unavailable players from starters/substitutes
     * - fills missing starter slots from available original substitutes, then additional squad players,
     *   choosing the best replacement by position compatibility and rating
     * - refills the bench from remaining available players (original substitutes preferred)
     */
    public EffectiveLineup generateEffectiveLineup(List<Player> squad) {
        List<Player> unavailable = squad.stream().filter(this::isUnavailable).toList();

        List<Player> availableOriginalStarters = squad.stream()
                .filter(Player::isStarter)
                .filter(p -> !isUnavailable(p))
                .toList();
        List<Player> missingStarters = squad.stream()
                .filter(Player::isStarter)
                .filter(this::isUnavailable)
                .toList();

        Set<Long> usedIds = new HashSet<>();
        List<Player> starters = new ArrayList<>();
        for (Player p : availableOriginalStarters) {
            starters.add(p);
            usedIds.add(p.getId());
        }

        List<Player> replacementPool = squad.stream()
                .filter(p -> !p.isStarter())
                .filter(p -> !isUnavailable(p))
                .filter(p -> !usedIds.contains(p.getId()))
                .toList();

        List<Replacement> replacements = new ArrayList<>();
        for (Player missing : missingStarters) {
            if (starters.size() >= TARGET_STARTERS) {
                break;
            }
            Player replacement = findBestReplacement(missing.getPosition(), replacementPool, usedIds);
            if (replacement == null) {
                continue;
            }
            starters.add(replacement);
            usedIds.add(replacement.getId());
            replacements.add(new Replacement(
                    missing.getFullName(),
                    replacement.getFullName(),
                    unavailabilityReason(missing),
                    missing.getPosition()));
        }

        // If still short of 11 (e.g. not enough original substitutes), top up from any remaining available players.
        if (starters.size() < TARGET_STARTERS) {
            List<Player> remainingAvailable = squad.stream()
                    .filter(p -> !isUnavailable(p))
                    .filter(p -> !usedIds.contains(p.getId()))
                    .sorted(Comparator.comparingInt(Player::getRating).reversed())
                    .toList();
            for (Player p : remainingAvailable) {
                if (starters.size() >= TARGET_STARTERS) {
                    break;
                }
                starters.add(p);
                usedIds.add(p.getId());
            }
        }

        List<Player> substitutes = squad.stream()
                .filter(p -> !isUnavailable(p))
                .filter(p -> !usedIds.contains(p.getId()))
                .sorted(Comparator
                        .comparing((Player p) -> !p.isSubstitute())
                        .thenComparing(Comparator.comparingInt(Player::getRating).reversed()))
                .toList();

        List<String> warnings = new ArrayList<>();
        if (starters.size() < TARGET_STARTERS) {
            warnings.add("Only " + starters.size() + " available player" + (starters.size() == 1 ? "" : "s")
                    + " — team cannot field a full 11-player lineup.");
        }
        if (substitutes.size() < TARGET_SUBSTITUTES) {
            warnings.add("Only " + substitutes.size() + " substitute" + (substitutes.size() == 1 ? "" : "s")
                    + " available for the bench.");
        }

        boolean effectiveLineupGenerated = !replacements.isEmpty() || !missingStarters.isEmpty();

        return new EffectiveLineup(starters, substitutes, unavailable, replacements, warnings, effectiveLineupGenerated);
    }

    /**
     * Picks the best replacement for a missing starter at {@code missingPosition} from {@code candidates}:
     * same position scores highest, then compatible nearby positions in the documented preference order,
     * then by rating (higher first); original substitutes are preferred over additional squad players,
     * and a player already used elsewhere in the lineup is never selected twice.
     * A goalkeeper slot is only ever filled by a goalkeeper, and a goalkeeper never fills an outfield slot.
     */
    Player findBestReplacement(Position missingPosition, List<Player> candidates, Set<Long> usedIds) {
        Player best = null;
        int bestScore = Integer.MIN_VALUE;

        for (Player candidate : candidates) {
            if (usedIds.contains(candidate.getId())) {
                continue;
            }
            if (missingPosition == Position.GK || candidate.getPosition() == Position.GK) {
                if (candidate.getPosition() != missingPosition) {
                    continue;
                }
            }

            int tierScore = positionTierScore(missingPosition, candidate.getPosition());
            if (tierScore < 0) {
                continue;
            }

            int score = tierScore * 1000
                    + (candidate.isSubstitute() ? 100 : 0)
                    + candidate.getRating();

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Same position scores highest (100); compatible nearby positions score in the documented
     * preference order (earlier entries score higher, but always below an exact match);
     * incompatible positions are excluded (-1).
     */
    private int positionTierScore(Position missingPosition, Position candidatePosition) {
        if (candidatePosition == missingPosition) {
            return 100;
        }
        List<Position> compatible = COMPATIBLE_POSITIONS.getOrDefault(missingPosition, List.of());
        int idx = compatible.indexOf(candidatePosition);
        if (idx < 0) {
            return -1;
        }
        return 50 - idx;
    }

    private String unavailabilityReason(Player player) {
        boolean injured = player.getStatus() == PlayerStatus.INJURED && player.getInjuryMatchesRemaining() > 0;
        boolean suspended = player.getStatus() == PlayerStatus.SUSPENDED && player.getSuspensionMatchesRemaining() > 0;
        if (injured && suspended) {
            return "Injured + Suspended";
        }
        if (suspended) {
            return "Suspended";
        }
        return "Injured";
    }
}

package com.footballsim.dto;

import java.util.List;

/**
 * Per-team squad-completeness report produced while validating the league
 * real-data file — helps fill in and verify real squads before bulk import.
 */
public class TeamSquadSummary {

    private String teamName;
    private int playersCount;
    private int startersCount;
    private int substitutesCount;
    private int unavailableCount;
    private boolean hasExactlyOneStartingGoalkeeper;
    private boolean hasAtLeast7Substitutes;
    private boolean hasExactly11Starters;
    private List<Integer> duplicateJerseyNumbers;
    private List<Integer> duplicateLineupOrders;
    private boolean invalidStarterSubstituteOverlap;
    private List<String> missingPositions;
    private String status;

    public static TeamSquadSummary of(String teamName, int playersCount, int startersCount, int substitutesCount,
                                       int unavailableCount, boolean hasExactlyOneStartingGoalkeeper,
                                       boolean hasAtLeast7Substitutes, boolean hasExactly11Starters,
                                       List<Integer> duplicateJerseyNumbers, List<Integer> duplicateLineupOrders,
                                       boolean invalidStarterSubstituteOverlap, List<String> missingPositions,
                                       String status) {
        TeamSquadSummary s = new TeamSquadSummary();
        s.teamName = teamName;
        s.playersCount = playersCount;
        s.startersCount = startersCount;
        s.substitutesCount = substitutesCount;
        s.unavailableCount = unavailableCount;
        s.hasExactlyOneStartingGoalkeeper = hasExactlyOneStartingGoalkeeper;
        s.hasAtLeast7Substitutes = hasAtLeast7Substitutes;
        s.hasExactly11Starters = hasExactly11Starters;
        s.duplicateJerseyNumbers = duplicateJerseyNumbers;
        s.duplicateLineupOrders = duplicateLineupOrders;
        s.invalidStarterSubstituteOverlap = invalidStarterSubstituteOverlap;
        s.missingPositions = missingPositions;
        s.status = status;
        return s;
    }

    public static TeamSquadSummary missing(String teamName, List<String> allPositions) {
        return of(teamName, 0, 0, 0, 0, false, false, false,
                List.of(), List.of(), false, allPositions, "MISSING");
    }

    public String getTeamName() { return teamName; }
    public int getPlayersCount() { return playersCount; }
    public int getStartersCount() { return startersCount; }
    public int getSubstitutesCount() { return substitutesCount; }
    public int getUnavailableCount() { return unavailableCount; }
    public boolean isHasExactlyOneStartingGoalkeeper() { return hasExactlyOneStartingGoalkeeper; }
    public boolean isHasAtLeast7Substitutes() { return hasAtLeast7Substitutes; }
    public boolean isHasExactly11Starters() { return hasExactly11Starters; }
    public List<Integer> getDuplicateJerseyNumbers() { return duplicateJerseyNumbers; }
    public List<Integer> getDuplicateLineupOrders() { return duplicateLineupOrders; }
    public boolean isInvalidStarterSubstituteOverlap() { return invalidStarterSubstituteOverlap; }
    public List<String> getMissingPositions() { return missingPositions; }
    public String getStatus() { return status; }
}

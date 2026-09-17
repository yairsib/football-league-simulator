package com.footballsim.dto;

import java.util.List;
import java.util.Map;

public class AdminOverviewResponse {

    // ── Data state ────────────────────────────────────────────────────────────
    private int teamsCount;
    private int playersCount;
    private int completeSquadsCount;
    private int missingSquadsCount;
    private boolean scheduleGenerated;
    /** True only in a clean pre-season state (no opened/finished rounds, results, events, or bets). */
    private boolean leagueDataImportAllowed;
    private int totalRounds;
    private int totalMatches;

    // ── Round state ───────────────────────────────────────────────────────────
    private Integer currentRound;
    private Integer nextRoundToOpen;
    private Integer nextRoundToSimulate;
    private Map<String, Long> roundsByStatus;

    // ── Betting state ─────────────────────────────────────────────────────────
    private int openBetsCount;
    private int settledBetsCount;
    private int cancelledBetsCount;
    private boolean seasonBetsOpen;
    private String seasonBetsStatusReason;

    // ── Recommended actions ───────────────────────────────────────────────────
    private List<String> recommendedActions;

    public int getTeamsCount() { return teamsCount; }
    public void setTeamsCount(int teamsCount) { this.teamsCount = teamsCount; }

    public int getPlayersCount() { return playersCount; }
    public void setPlayersCount(int playersCount) { this.playersCount = playersCount; }

    public int getCompleteSquadsCount() { return completeSquadsCount; }
    public void setCompleteSquadsCount(int completeSquadsCount) { this.completeSquadsCount = completeSquadsCount; }

    public int getMissingSquadsCount() { return missingSquadsCount; }
    public void setMissingSquadsCount(int missingSquadsCount) { this.missingSquadsCount = missingSquadsCount; }

    public boolean isScheduleGenerated() { return scheduleGenerated; }
    public void setScheduleGenerated(boolean scheduleGenerated) { this.scheduleGenerated = scheduleGenerated; }

    public boolean isLeagueDataImportAllowed() { return leagueDataImportAllowed; }
    public void setLeagueDataImportAllowed(boolean leagueDataImportAllowed) { this.leagueDataImportAllowed = leagueDataImportAllowed; }

    public int getTotalRounds() { return totalRounds; }
    public void setTotalRounds(int totalRounds) { this.totalRounds = totalRounds; }

    public int getTotalMatches() { return totalMatches; }
    public void setTotalMatches(int totalMatches) { this.totalMatches = totalMatches; }

    public Integer getCurrentRound() { return currentRound; }
    public void setCurrentRound(Integer currentRound) { this.currentRound = currentRound; }

    public Integer getNextRoundToOpen() { return nextRoundToOpen; }
    public void setNextRoundToOpen(Integer nextRoundToOpen) { this.nextRoundToOpen = nextRoundToOpen; }

    public Integer getNextRoundToSimulate() { return nextRoundToSimulate; }
    public void setNextRoundToSimulate(Integer nextRoundToSimulate) { this.nextRoundToSimulate = nextRoundToSimulate; }

    public Map<String, Long> getRoundsByStatus() { return roundsByStatus; }
    public void setRoundsByStatus(Map<String, Long> roundsByStatus) { this.roundsByStatus = roundsByStatus; }

    public int getOpenBetsCount() { return openBetsCount; }
    public void setOpenBetsCount(int openBetsCount) { this.openBetsCount = openBetsCount; }

    public int getSettledBetsCount() { return settledBetsCount; }
    public void setSettledBetsCount(int settledBetsCount) { this.settledBetsCount = settledBetsCount; }

    public int getCancelledBetsCount() { return cancelledBetsCount; }
    public void setCancelledBetsCount(int cancelledBetsCount) { this.cancelledBetsCount = cancelledBetsCount; }

    public boolean isSeasonBetsOpen() { return seasonBetsOpen; }
    public void setSeasonBetsOpen(boolean seasonBetsOpen) { this.seasonBetsOpen = seasonBetsOpen; }

    public String getSeasonBetsStatusReason() { return seasonBetsStatusReason; }
    public void setSeasonBetsStatusReason(String seasonBetsStatusReason) { this.seasonBetsStatusReason = seasonBetsStatusReason; }

    public List<String> getRecommendedActions() { return recommendedActions; }
    public void setRecommendedActions(List<String> recommendedActions) { this.recommendedActions = recommendedActions; }
}

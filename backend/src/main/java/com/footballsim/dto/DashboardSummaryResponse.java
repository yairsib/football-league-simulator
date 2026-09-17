package com.footballsim.dto;

import java.math.BigDecimal;
import java.util.List;

public class DashboardSummaryResponse {

    // ── User / betting summary ────────────────────────────────────────────────
    private BigDecimal balance;
    private int openBetsCount;
    private int settledBetsCount;
    private int wonBetsCount;
    private int lostBetsCount;
    private int cancelledBetsCount;
    private BigDecimal totalStakedOpen;
    private BigDecimal totalProfitSettled;
    private BigDecimal potentialWinningsOpen;
    private List<BetResponse> recentBets;

    // ── Season / match summary ────────────────────────────────────────────────
    private int totalRounds;
    private Integer currentRoundNumber;
    private Integer nextRoundNumberToPlay;
    private int finishedRoundsCount;
    private int totalMatches;
    private int finishedMatchesCount;
    private Integer nextOpenRoundNumber;
    private boolean seasonBetsOpen;
    private String seasonBetsStatusReason;

    // ── League highlights ─────────────────────────────────────────────────────
    private String leaderTeamName;
    private Long leaderTeamId;
    private Integer leaderPoints;
    private String topScorerName;
    private String topScorerTeam;
    private Integer topScorerGoals;
    private String topAssisterName;
    private String topAssisterTeam;
    private Integer topAssisterAssists;
    private String redCardLeaderName;
    private String redCardLeaderTeam;
    private Integer redCardLeaderCards;

    // ── Season completion ─────────────────────────────────────────────────────
    private boolean seasonComplete;

    public boolean isSeasonComplete() { return seasonComplete; }
    public void setSeasonComplete(boolean seasonComplete) { this.seasonComplete = seasonComplete; }

    // ── Quick actions ─────────────────────────────────────────────────────────
    private boolean canPlaceSeasonBets;
    private boolean canPlaceMatchBets;
    private boolean hasOpenBets;
    private boolean hasFinishedMatches;
    private boolean adminCanGenerateSchedule;
    private boolean adminCanOpenNextRound;
    private boolean adminCanSimulateNextRound;

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public int getOpenBetsCount() { return openBetsCount; }
    public void setOpenBetsCount(int openBetsCount) { this.openBetsCount = openBetsCount; }

    public int getSettledBetsCount() { return settledBetsCount; }
    public void setSettledBetsCount(int settledBetsCount) { this.settledBetsCount = settledBetsCount; }

    public int getWonBetsCount() { return wonBetsCount; }
    public void setWonBetsCount(int wonBetsCount) { this.wonBetsCount = wonBetsCount; }

    public int getLostBetsCount() { return lostBetsCount; }
    public void setLostBetsCount(int lostBetsCount) { this.lostBetsCount = lostBetsCount; }

    public int getCancelledBetsCount() { return cancelledBetsCount; }
    public void setCancelledBetsCount(int cancelledBetsCount) { this.cancelledBetsCount = cancelledBetsCount; }

    public BigDecimal getTotalStakedOpen() { return totalStakedOpen; }
    public void setTotalStakedOpen(BigDecimal totalStakedOpen) { this.totalStakedOpen = totalStakedOpen; }

    public BigDecimal getTotalProfitSettled() { return totalProfitSettled; }
    public void setTotalProfitSettled(BigDecimal totalProfitSettled) { this.totalProfitSettled = totalProfitSettled; }

    public BigDecimal getPotentialWinningsOpen() { return potentialWinningsOpen; }
    public void setPotentialWinningsOpen(BigDecimal potentialWinningsOpen) { this.potentialWinningsOpen = potentialWinningsOpen; }

    public List<BetResponse> getRecentBets() { return recentBets; }
    public void setRecentBets(List<BetResponse> recentBets) { this.recentBets = recentBets; }

    public int getTotalRounds() { return totalRounds; }
    public void setTotalRounds(int totalRounds) { this.totalRounds = totalRounds; }

    public Integer getCurrentRoundNumber() { return currentRoundNumber; }
    public void setCurrentRoundNumber(Integer currentRoundNumber) { this.currentRoundNumber = currentRoundNumber; }

    public Integer getNextRoundNumberToPlay() { return nextRoundNumberToPlay; }
    public void setNextRoundNumberToPlay(Integer nextRoundNumberToPlay) { this.nextRoundNumberToPlay = nextRoundNumberToPlay; }

    public int getFinishedRoundsCount() { return finishedRoundsCount; }
    public void setFinishedRoundsCount(int finishedRoundsCount) { this.finishedRoundsCount = finishedRoundsCount; }

    public int getTotalMatches() { return totalMatches; }
    public void setTotalMatches(int totalMatches) { this.totalMatches = totalMatches; }

    public int getFinishedMatchesCount() { return finishedMatchesCount; }
    public void setFinishedMatchesCount(int finishedMatchesCount) { this.finishedMatchesCount = finishedMatchesCount; }

    public Integer getNextOpenRoundNumber() { return nextOpenRoundNumber; }
    public void setNextOpenRoundNumber(Integer nextOpenRoundNumber) { this.nextOpenRoundNumber = nextOpenRoundNumber; }

    public boolean isSeasonBetsOpen() { return seasonBetsOpen; }
    public void setSeasonBetsOpen(boolean seasonBetsOpen) { this.seasonBetsOpen = seasonBetsOpen; }

    public String getSeasonBetsStatusReason() { return seasonBetsStatusReason; }
    public void setSeasonBetsStatusReason(String seasonBetsStatusReason) { this.seasonBetsStatusReason = seasonBetsStatusReason; }

    public String getLeaderTeamName() { return leaderTeamName; }
    public void setLeaderTeamName(String leaderTeamName) { this.leaderTeamName = leaderTeamName; }

    public Long getLeaderTeamId() { return leaderTeamId; }
    public void setLeaderTeamId(Long leaderTeamId) { this.leaderTeamId = leaderTeamId; }

    public Integer getLeaderPoints() { return leaderPoints; }
    public void setLeaderPoints(Integer leaderPoints) { this.leaderPoints = leaderPoints; }

    public String getTopScorerName() { return topScorerName; }
    public void setTopScorerName(String topScorerName) { this.topScorerName = topScorerName; }

    public String getTopScorerTeam() { return topScorerTeam; }
    public void setTopScorerTeam(String topScorerTeam) { this.topScorerTeam = topScorerTeam; }

    public Integer getTopScorerGoals() { return topScorerGoals; }
    public void setTopScorerGoals(Integer topScorerGoals) { this.topScorerGoals = topScorerGoals; }

    public String getTopAssisterName() { return topAssisterName; }
    public void setTopAssisterName(String topAssisterName) { this.topAssisterName = topAssisterName; }

    public String getTopAssisterTeam() { return topAssisterTeam; }
    public void setTopAssisterTeam(String topAssisterTeam) { this.topAssisterTeam = topAssisterTeam; }

    public Integer getTopAssisterAssists() { return topAssisterAssists; }
    public void setTopAssisterAssists(Integer topAssisterAssists) { this.topAssisterAssists = topAssisterAssists; }

    public String getRedCardLeaderName() { return redCardLeaderName; }
    public void setRedCardLeaderName(String redCardLeaderName) { this.redCardLeaderName = redCardLeaderName; }

    public String getRedCardLeaderTeam() { return redCardLeaderTeam; }
    public void setRedCardLeaderTeam(String redCardLeaderTeam) { this.redCardLeaderTeam = redCardLeaderTeam; }

    public Integer getRedCardLeaderCards() { return redCardLeaderCards; }
    public void setRedCardLeaderCards(Integer redCardLeaderCards) { this.redCardLeaderCards = redCardLeaderCards; }

    public boolean isCanPlaceSeasonBets() { return canPlaceSeasonBets; }
    public void setCanPlaceSeasonBets(boolean canPlaceSeasonBets) { this.canPlaceSeasonBets = canPlaceSeasonBets; }

    public boolean isCanPlaceMatchBets() { return canPlaceMatchBets; }
    public void setCanPlaceMatchBets(boolean canPlaceMatchBets) { this.canPlaceMatchBets = canPlaceMatchBets; }

    public boolean isHasOpenBets() { return hasOpenBets; }
    public void setHasOpenBets(boolean hasOpenBets) { this.hasOpenBets = hasOpenBets; }

    public boolean isHasFinishedMatches() { return hasFinishedMatches; }
    public void setHasFinishedMatches(boolean hasFinishedMatches) { this.hasFinishedMatches = hasFinishedMatches; }

    public boolean isAdminCanGenerateSchedule() { return adminCanGenerateSchedule; }
    public void setAdminCanGenerateSchedule(boolean adminCanGenerateSchedule) { this.adminCanGenerateSchedule = adminCanGenerateSchedule; }

    public boolean isAdminCanOpenNextRound() { return adminCanOpenNextRound; }
    public void setAdminCanOpenNextRound(boolean adminCanOpenNextRound) { this.adminCanOpenNextRound = adminCanOpenNextRound; }

    public boolean isAdminCanSimulateNextRound() { return adminCanSimulateNextRound; }
    public void setAdminCanSimulateNextRound(boolean adminCanSimulateNextRound) { this.adminCanSimulateNextRound = adminCanSimulateNextRound; }
}

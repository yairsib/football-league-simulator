package com.footballsim.dto;

public class SeasonResetResponse {

    private int matchEventsDeleted;
    private int betsDeleted;
    private int openBetsRefunded;
    private int matchesReset;
    private int roundsReset;
    private int teamsReset;
    private int playersReset;
    private int teamBaselinesRestored;
    private int usersBalanceReset;
    private boolean scheduleRegenerated;
    private String message;

    public int getMatchEventsDeleted() { return matchEventsDeleted; }
    public void setMatchEventsDeleted(int matchEventsDeleted) { this.matchEventsDeleted = matchEventsDeleted; }

    public int getBetsDeleted() { return betsDeleted; }
    public void setBetsDeleted(int betsDeleted) { this.betsDeleted = betsDeleted; }

    public int getOpenBetsRefunded() { return openBetsRefunded; }
    public void setOpenBetsRefunded(int openBetsRefunded) { this.openBetsRefunded = openBetsRefunded; }

    public int getMatchesReset() { return matchesReset; }
    public void setMatchesReset(int matchesReset) { this.matchesReset = matchesReset; }

    public int getRoundsReset() { return roundsReset; }
    public void setRoundsReset(int roundsReset) { this.roundsReset = roundsReset; }

    public int getTeamsReset() { return teamsReset; }
    public void setTeamsReset(int teamsReset) { this.teamsReset = teamsReset; }

    public int getPlayersReset() { return playersReset; }
    public void setPlayersReset(int playersReset) { this.playersReset = playersReset; }

    public int getTeamBaselinesRestored() { return teamBaselinesRestored; }
    public void setTeamBaselinesRestored(int teamBaselinesRestored) { this.teamBaselinesRestored = teamBaselinesRestored; }

    public int getUsersBalanceReset() { return usersBalanceReset; }
    public void setUsersBalanceReset(int usersBalanceReset) { this.usersBalanceReset = usersBalanceReset; }

    public boolean isScheduleRegenerated() { return scheduleRegenerated; }
    public void setScheduleRegenerated(boolean scheduleRegenerated) { this.scheduleRegenerated = scheduleRegenerated; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

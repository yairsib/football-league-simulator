package com.footballsim.dto;

import com.footballsim.entity.Team;

public class LeagueTableResponse {

    private int position;
    private Long teamId;
    private String teamName;
    private int played;
    private int wins;
    private int draws;
    private int losses;
    private int goalsFor;
    private int goalsAgainst;
    private int goalDifference;
    private int points;

    public static LeagueTableResponse from(int position, Team team) {
        LeagueTableResponse r = new LeagueTableResponse();
        r.position = position;
        r.teamId = team.getId();
        r.teamName = team.getName();
        r.played = team.getPlayed();
        r.wins = team.getWins();
        r.draws = team.getDraws();
        r.losses = team.getLosses();
        r.goalsFor = team.getGoalsFor();
        r.goalsAgainst = team.getGoalsAgainst();
        r.goalDifference = team.getGoalsFor() - team.getGoalsAgainst();
        r.points = team.getPoints();
        return r;
    }

    public int getPosition() { return position; }
    public Long getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public int getPlayed() { return played; }
    public int getWins() { return wins; }
    public int getDraws() { return draws; }
    public int getLosses() { return losses; }
    public int getGoalsFor() { return goalsFor; }
    public int getGoalsAgainst() { return goalsAgainst; }
    public int getGoalDifference() { return goalDifference; }
    public int getPoints() { return points; }
}

package com.footballsim.dto;

import com.footballsim.entity.Team;

public class TeamResponse {

    private Long id;
    private String name;
    private int skillLevel;
    private int morale;
    private Integer baselineSkillLevel;
    private Integer baselineMorale;
    private int injuries;
    private int played;
    private int wins;
    private int draws;
    private int losses;
    private int goalsFor;
    private int goalsAgainst;
    private int goalDifference;
    private int points;

    public static TeamResponse from(Team team) {
        TeamResponse r = new TeamResponse();
        r.id = team.getId();
        r.name = team.getName();
        r.skillLevel = team.getSkillLevel();
        r.morale = team.getMorale();
        r.baselineSkillLevel = team.getBaselineSkillLevel();
        r.baselineMorale = team.getBaselineMorale();
        r.injuries = team.getInjuries();
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

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getSkillLevel() { return skillLevel; }
    public int getMorale() { return morale; }
    public Integer getBaselineSkillLevel() { return baselineSkillLevel; }
    public Integer getBaselineMorale() { return baselineMorale; }
    public int getInjuries() { return injuries; }
    public int getPlayed() { return played; }
    public int getWins() { return wins; }
    public int getDraws() { return draws; }
    public int getLosses() { return losses; }
    public int getGoalsFor() { return goalsFor; }
    public int getGoalsAgainst() { return goalsAgainst; }
    public int getGoalDifference() { return goalDifference; }
    public int getPoints() { return points; }
}

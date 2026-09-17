package com.footballsim.dto;

import com.footballsim.entity.Team;

import java.util.List;

public class TeamSquadResponse {

    private Long teamId;
    private String teamName;
    private String formation;
    private List<PlayerResponse> players;

    public static TeamSquadResponse of(Team team, List<PlayerResponse> players) {
        TeamSquadResponse r = new TeamSquadResponse();
        r.teamId = team.getId();
        r.teamName = team.getName();
        r.formation = team.getDefaultFormation();
        r.players = players;
        return r;
    }

    public Long getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public String getFormation() { return formation; }
    public List<PlayerResponse> getPlayers() { return players; }
}

package com.footballsim.dto;

import com.footballsim.entity.Team;

import java.util.List;

public class TeamLineupResponse {

    private Long teamId;
    private String teamName;
    private String formation;
    private List<PlayerResponse> starters;
    private List<PlayerResponse> substitutes;
    private List<PlayerResponse> unavailablePlayers;
    private boolean effectiveLineupGenerated;
    private List<String> lineupWarnings;
    private List<ReplacedPlayerInfo> replacedPlayers;

    public static TeamLineupResponse of(Team team,
                                         List<PlayerResponse> starters,
                                         List<PlayerResponse> substitutes,
                                         List<PlayerResponse> unavailablePlayers,
                                         boolean effectiveLineupGenerated,
                                         List<String> lineupWarnings,
                                         List<ReplacedPlayerInfo> replacedPlayers) {
        TeamLineupResponse r = new TeamLineupResponse();
        r.teamId = team.getId();
        r.teamName = team.getName();
        r.formation = team.getDefaultFormation();
        r.starters = starters;
        r.substitutes = substitutes;
        r.unavailablePlayers = unavailablePlayers;
        r.effectiveLineupGenerated = effectiveLineupGenerated;
        r.lineupWarnings = lineupWarnings;
        r.replacedPlayers = replacedPlayers;
        return r;
    }

    public Long getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public String getFormation() { return formation; }
    public List<PlayerResponse> getStarters() { return starters; }
    public List<PlayerResponse> getSubstitutes() { return substitutes; }
    public List<PlayerResponse> getUnavailablePlayers() { return unavailablePlayers; }
    public boolean isEffectiveLineupGenerated() { return effectiveLineupGenerated; }
    public List<String> getLineupWarnings() { return lineupWarnings; }
    public List<ReplacedPlayerInfo> getReplacedPlayers() { return replacedPlayers; }
}

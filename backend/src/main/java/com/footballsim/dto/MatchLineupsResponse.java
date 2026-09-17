package com.footballsim.dto;

import com.footballsim.entity.Match;
import com.footballsim.enums.WeatherCondition;

import java.util.List;

public class MatchLineupsResponse {

    private Long matchId;
    private String homeTeamName;
    private String awayTeamName;
    private String homeFormation;
    private String awayFormation;
    private List<PlayerResponse> homeStarters;
    private List<PlayerResponse> homeSubstitutes;
    private List<PlayerResponse> homeUnavailablePlayers;
    private List<PlayerResponse> awayStarters;
    private List<PlayerResponse> awaySubstitutes;
    private List<PlayerResponse> awayUnavailablePlayers;
    private boolean homeEffectiveLineupGenerated;
    private List<String> homeLineupWarnings;
    private List<ReplacedPlayerInfo> homeReplacedPlayers;
    private boolean awayEffectiveLineupGenerated;
    private List<String> awayLineupWarnings;
    private List<ReplacedPlayerInfo> awayReplacedPlayers;
    private WeatherCondition weatherCondition;
    private Integer weatherImpact;

    public static MatchLineupsResponse of(Match match, TeamLineupResponse home, TeamLineupResponse away) {
        MatchLineupsResponse r = new MatchLineupsResponse();
        r.matchId = match.getId();
        r.homeTeamName = home.getTeamName();
        r.awayTeamName = away.getTeamName();
        r.homeFormation = home.getFormation();
        r.awayFormation = away.getFormation();
        r.homeStarters = home.getStarters();
        r.homeSubstitutes = home.getSubstitutes();
        r.homeUnavailablePlayers = home.getUnavailablePlayers();
        r.awayStarters = away.getStarters();
        r.awaySubstitutes = away.getSubstitutes();
        r.awayUnavailablePlayers = away.getUnavailablePlayers();
        r.homeEffectiveLineupGenerated = home.isEffectiveLineupGenerated();
        r.homeLineupWarnings = home.getLineupWarnings();
        r.homeReplacedPlayers = home.getReplacedPlayers();
        r.awayEffectiveLineupGenerated = away.isEffectiveLineupGenerated();
        r.awayLineupWarnings = away.getLineupWarnings();
        r.awayReplacedPlayers = away.getReplacedPlayers();
        r.weatherCondition = match.getWeatherCondition();
        r.weatherImpact = match.getWeatherImpact();
        return r;
    }

    public Long getMatchId() { return matchId; }
    public String getHomeTeamName() { return homeTeamName; }
    public String getAwayTeamName() { return awayTeamName; }
    public String getHomeFormation() { return homeFormation; }
    public String getAwayFormation() { return awayFormation; }
    public List<PlayerResponse> getHomeStarters() { return homeStarters; }
    public List<PlayerResponse> getHomeSubstitutes() { return homeSubstitutes; }
    public List<PlayerResponse> getHomeUnavailablePlayers() { return homeUnavailablePlayers; }
    public List<PlayerResponse> getAwayStarters() { return awayStarters; }
    public List<PlayerResponse> getAwaySubstitutes() { return awaySubstitutes; }
    public List<PlayerResponse> getAwayUnavailablePlayers() { return awayUnavailablePlayers; }
    public boolean isHomeEffectiveLineupGenerated() { return homeEffectiveLineupGenerated; }
    public List<String> getHomeLineupWarnings() { return homeLineupWarnings; }
    public List<ReplacedPlayerInfo> getHomeReplacedPlayers() { return homeReplacedPlayers; }
    public boolean isAwayEffectiveLineupGenerated() { return awayEffectiveLineupGenerated; }
    public List<String> getAwayLineupWarnings() { return awayLineupWarnings; }
    public List<ReplacedPlayerInfo> getAwayReplacedPlayers() { return awayReplacedPlayers; }
    public WeatherCondition getWeatherCondition() { return weatherCondition; }
    public Integer getWeatherImpact() { return weatherImpact; }
}

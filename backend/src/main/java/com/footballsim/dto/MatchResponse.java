package com.footballsim.dto;

import com.footballsim.entity.Match;
import com.footballsim.enums.MatchStatus;
import com.footballsim.enums.WeatherCondition;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MatchResponse {

    private Long id;
    private int roundNumber;
    private Long homeTeamId;
    private String homeTeamName;
    private Long awayTeamId;
    private String awayTeamName;
    private Integer homeGoals;
    private Integer awayGoals;
    private MatchStatus status;
    private boolean bettingOpen;
    private BigDecimal homeOdds;
    private BigDecimal drawOdds;
    private BigDecimal awayOdds;
    private BigDecimal handicapHomeMinusOneOdds;
    private BigDecimal handicapDrawOdds;
    private BigDecimal handicapAwayPlusOneOdds;
    private LocalDateTime matchDate;
    private WeatherCondition weatherCondition;
    private Integer weatherImpact;

    public static MatchResponse from(Match match) {
        MatchResponse r = new MatchResponse();
        r.id = match.getId();
        r.roundNumber = match.getRound().getRoundNumber();
        r.homeTeamId = match.getHomeTeam().getId();
        r.homeTeamName = match.getHomeTeam().getName();
        r.awayTeamId = match.getAwayTeam().getId();
        r.awayTeamName = match.getAwayTeam().getName();
        r.homeGoals = match.getHomeGoals();
        r.awayGoals = match.getAwayGoals();
        r.status = match.getStatus();
        r.bettingOpen = match.isBettingOpen();
        r.homeOdds = match.getHomeOdds();
        r.drawOdds = match.getDrawOdds();
        r.awayOdds = match.getAwayOdds();
        r.handicapHomeMinusOneOdds = match.getHandicapHomeMinusOneOdds();
        r.handicapDrawOdds = match.getHandicapDrawOdds();
        r.handicapAwayPlusOneOdds = match.getHandicapAwayPlusOneOdds();
        r.matchDate = match.getMatchDate();
        r.weatherCondition = match.getWeatherCondition();
        r.weatherImpact = match.getWeatherImpact();
        return r;
    }

    public Long getId() { return id; }
    public int getRoundNumber() { return roundNumber; }
    public Long getHomeTeamId() { return homeTeamId; }
    public String getHomeTeamName() { return homeTeamName; }
    public Long getAwayTeamId() { return awayTeamId; }
    public String getAwayTeamName() { return awayTeamName; }
    public Integer getHomeGoals() { return homeGoals; }
    public Integer getAwayGoals() { return awayGoals; }
    public MatchStatus getStatus() { return status; }
    public boolean isBettingOpen() { return bettingOpen; }
    public BigDecimal getHomeOdds() { return homeOdds; }
    public BigDecimal getDrawOdds() { return drawOdds; }
    public BigDecimal getAwayOdds() { return awayOdds; }
    public BigDecimal getHandicapHomeMinusOneOdds() { return handicapHomeMinusOneOdds; }
    public BigDecimal getHandicapDrawOdds() { return handicapDrawOdds; }
    public BigDecimal getHandicapAwayPlusOneOdds() { return handicapAwayPlusOneOdds; }
    public LocalDateTime getMatchDate() { return matchDate; }
    public WeatherCondition getWeatherCondition() { return weatherCondition; }
    public Integer getWeatherImpact() { return weatherImpact; }
}

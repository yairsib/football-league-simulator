package com.footballsim.dto;

import com.footballsim.enums.Position;

import java.math.BigDecimal;

public class TopScorerOddsOption {

    private Long playerId;
    private String fullName;
    private Long teamId;
    private String teamName;
    private Position position;
    private int rating;
    private BigDecimal odds;

    public TopScorerOddsOption(Long playerId, String fullName, Long teamId, String teamName,
                                Position position, int rating, BigDecimal odds) {
        this.playerId = playerId;
        this.fullName = fullName;
        this.teamId = teamId;
        this.teamName = teamName;
        this.position = position;
        this.rating = rating;
        this.odds = odds;
    }

    public Long getPlayerId() { return playerId; }
    public String getFullName() { return fullName; }
    public Long getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public Position getPosition() { return position; }
    public int getRating() { return rating; }
    public BigDecimal getOdds() { return odds; }
}

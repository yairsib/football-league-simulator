package com.footballsim.dto;

import com.footballsim.entity.Player;
import com.footballsim.enums.PlayerStatus;
import com.footballsim.enums.Position;

public class PlayerStatsResponse {

    private Long playerId;
    private String fullName;
    private Long teamId;
    private String teamName;
    private Position position;
    private Integer jerseyNumber;
    private int rating;
    private int goals;
    private int assists;
    private int redCards;
    private PlayerStatus status;
    private int injuryMatchesRemaining;
    private int suspensionMatchesRemaining;

    public static PlayerStatsResponse from(Player player) {
        PlayerStatsResponse r = new PlayerStatsResponse();
        r.playerId = player.getId();
        r.fullName = player.getFullName();
        r.teamId = player.getTeam().getId();
        r.teamName = player.getTeam().getName();
        r.position = player.getPosition();
        r.jerseyNumber = player.getJerseyNumber();
        r.rating = player.getRating();
        r.goals = player.getGoals();
        r.assists = player.getAssists();
        r.redCards = player.getRedCards();
        r.status = player.getStatus();
        r.injuryMatchesRemaining = player.getInjuryMatchesRemaining();
        r.suspensionMatchesRemaining = player.getSuspensionMatchesRemaining();
        return r;
    }

    public Long getPlayerId() { return playerId; }
    public String getFullName() { return fullName; }
    public Long getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public Position getPosition() { return position; }
    public Integer getJerseyNumber() { return jerseyNumber; }
    public int getRating() { return rating; }
    public int getGoals() { return goals; }
    public int getAssists() { return assists; }
    public int getRedCards() { return redCards; }
    public PlayerStatus getStatus() { return status; }
    public int getInjuryMatchesRemaining() { return injuryMatchesRemaining; }
    public int getSuspensionMatchesRemaining() { return suspensionMatchesRemaining; }
}

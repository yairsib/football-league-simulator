package com.footballsim.dto;

import com.footballsim.entity.Player;
import com.footballsim.enums.PlayerStatus;
import com.footballsim.enums.Position;

public class PlayerResponse {

    private Long id;
    private String fullName;
    private Position position;
    private Integer jerseyNumber;
    private int rating;
    private PlayerStatus status;
    private String injuryDescription;
    private Integer injuredUntilRound;
    private int injuryMatchesRemaining;
    private Integer injuryMatchesTotal;
    private int goals;
    private int assists;
    private int redCards;
    private int suspensionMatchesRemaining;
    private boolean starter;
    private boolean substitute;
    private Integer lineupOrder;

    public static PlayerResponse from(Player player) {
        PlayerResponse r = new PlayerResponse();
        r.id = player.getId();
        r.fullName = player.getFullName();
        r.position = player.getPosition();
        r.jerseyNumber = player.getJerseyNumber();
        r.rating = player.getRating();
        r.status = player.getStatus();
        r.injuryDescription = player.getInjuryDescription();
        r.injuredUntilRound = player.getInjuredUntilRound();
        r.injuryMatchesRemaining = player.getInjuryMatchesRemaining();
        r.injuryMatchesTotal = player.getInjuryMatchesTotal();
        r.goals = player.getGoals();
        r.assists = player.getAssists();
        r.redCards = player.getRedCards();
        r.suspensionMatchesRemaining = player.getSuspensionMatchesRemaining();
        r.starter = player.isStarter();
        r.substitute = player.isSubstitute();
        r.lineupOrder = player.getLineupOrder();
        return r;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public Position getPosition() { return position; }
    public Integer getJerseyNumber() { return jerseyNumber; }
    public int getRating() { return rating; }
    public PlayerStatus getStatus() { return status; }
    public String getInjuryDescription() { return injuryDescription; }
    public Integer getInjuredUntilRound() { return injuredUntilRound; }
    public int getInjuryMatchesRemaining() { return injuryMatchesRemaining; }
    public Integer getInjuryMatchesTotal() { return injuryMatchesTotal; }
    public int getGoals() { return goals; }
    public int getAssists() { return assists; }
    public int getRedCards() { return redCards; }
    public int getSuspensionMatchesRemaining() { return suspensionMatchesRemaining; }
    public boolean isStarter() { return starter; }
    public boolean isSubstitute() { return substitute; }
    public Integer getLineupOrder() { return lineupOrder; }
}

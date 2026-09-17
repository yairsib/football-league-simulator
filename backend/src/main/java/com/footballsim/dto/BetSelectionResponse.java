package com.footballsim.dto;

import com.footballsim.entity.Bet;
import com.footballsim.entity.BetSelection;
import com.footballsim.enums.Prediction;

import java.math.BigDecimal;

public class BetSelectionResponse {

    private Long matchId;
    private String homeTeamName;
    private String awayTeamName;
    private Prediction prediction;
    private BigDecimal odds;

    public static BetSelectionResponse from(BetSelection selection) {
        BetSelectionResponse r = new BetSelectionResponse();
        r.matchId = selection.getMatch().getId();
        r.homeTeamName = selection.getMatch().getHomeTeam().getName();
        r.awayTeamName = selection.getMatch().getAwayTeam().getName();
        r.prediction = selection.getPrediction();
        r.odds = selection.getOddsSnapshot();
        return r;
    }

    /** Mirrors a SINGLE bet's match/prediction/odds as a synthetic single-entry selection. */
    public static BetSelectionResponse fromSingleBet(Bet bet) {
        BetSelectionResponse r = new BetSelectionResponse();
        r.matchId = bet.getMatch().getId();
        r.homeTeamName = bet.getMatch().getHomeTeam().getName();
        r.awayTeamName = bet.getMatch().getAwayTeam().getName();
        r.prediction = bet.getPrediction();
        r.odds = bet.getOdds();
        return r;
    }

    public Long getMatchId() { return matchId; }
    public String getHomeTeamName() { return homeTeamName; }
    public String getAwayTeamName() { return awayTeamName; }
    public Prediction getPrediction() { return prediction; }
    public BigDecimal getOdds() { return odds; }
}

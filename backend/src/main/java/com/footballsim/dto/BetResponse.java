package com.footballsim.dto;

import com.footballsim.entity.Bet;
import com.footballsim.enums.BetMarket;
import com.footballsim.enums.BetStatus;
import com.footballsim.enums.BetType;
import com.footballsim.enums.HandicapSelection;
import com.footballsim.enums.Prediction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class BetResponse {

    private Long id;
    private BetType betType;
    private Long matchId;
    private String homeTeamName;
    private String awayTeamName;
    private Prediction prediction;
    private BetMarket market;
    private Integer predictedHomeGoals;
    private Integer predictedAwayGoals;
    private HandicapSelection handicapSelection;
    private Long selectedTeamId;
    private String selectedTeamName;
    private Long selectedPlayerId;
    private String selectedPlayerName;
    private String displayLabel;
    private BigDecimal amount;
    private BigDecimal odds;
    private BigDecimal totalOdds;
    private BigDecimal possibleWin;
    private BetStatus status;
    private BigDecimal profit;
    private LocalDateTime createdAt;
    private LocalDateTime settledAt;
    private List<BetSelectionResponse> selections;

    /**
     * For SINGLE bets, {@code selections} contains exactly one entry mirroring the
     * top-level match/prediction/odds fields, so the frontend can render single and
     * combo bets with the same selections-list UI.
     */
    public static BetResponse from(Bet bet) {
        BetResponse r = new BetResponse();
        r.id = bet.getId();
        r.betType = bet.getBetType();
        r.amount = bet.getAmount();
        r.odds = bet.getOdds();
        r.totalOdds = bet.getTotalOdds();
        r.possibleWin = bet.getPossibleWin();
        r.status = bet.getStatus();
        r.profit = bet.getProfit();
        r.createdAt = bet.getCreatedAt();
        r.settledAt = bet.getSettledAt();
        r.market = bet.getMarket();

        if (bet.getBetType() == BetType.COMBO) {
            r.selections = bet.getSelections().stream().map(BetSelectionResponse::from).toList();
        } else if (bet.getMarket() == BetMarket.CHAMPION) {
            // Season bets have no match; selectedTeam holds the betted team
            r.selectedTeamId = bet.getSelectedTeam().getId();
            String teamDisplay = bet.getSelectedTeam().getDisplayName() != null
                    ? bet.getSelectedTeam().getDisplayName()
                    : bet.getSelectedTeam().getName();
            r.selectedTeamName = teamDisplay;
            r.displayLabel = "Champion: " + teamDisplay;
            r.selections = List.of();
        } else if (bet.getMarket() == BetMarket.TOP_SCORER) {
            // Season bets have no match; selectedPlayer holds the betted player
            r.selectedPlayerId = bet.getSelectedPlayer().getId();
            r.selectedPlayerName = bet.getSelectedPlayer().getFullName();
            r.selectedTeamId = bet.getSelectedPlayer().getTeam().getId();
            String teamDisplay = bet.getSelectedPlayer().getTeam().getDisplayName() != null
                    ? bet.getSelectedPlayer().getTeam().getDisplayName()
                    : bet.getSelectedPlayer().getTeam().getName();
            r.selectedTeamName = teamDisplay;
            r.displayLabel = "Top Scorer: " + r.selectedPlayerName;
            r.selections = List.of();
        } else {
            // MATCH_RESULT, CORRECT_SCORE, HANDICAP — match is never null here
            r.matchId = bet.getMatch().getId();
            r.homeTeamName = bet.getMatch().getHomeTeam().getName();
            r.awayTeamName = bet.getMatch().getAwayTeam().getName();
            r.prediction = bet.getPrediction();
            r.selections = List.of(BetSelectionResponse.fromSingleBet(bet));

            if (bet.getMarket() == BetMarket.CORRECT_SCORE) {
                r.predictedHomeGoals = bet.getPredictedHomeGoals();
                r.predictedAwayGoals = bet.getPredictedAwayGoals();
                r.displayLabel = "Correct Score " + bet.getPredictedHomeGoals() + "-" + bet.getPredictedAwayGoals();
            } else if (bet.getMarket() == BetMarket.HANDICAP) {
                r.handicapSelection = bet.getHandicapSelection();
                r.displayLabel = switch (bet.getHandicapSelection()) {
                    case HOME_MINUS_ONE -> "Home -1";
                    case HANDICAP_DRAW  -> "Handicap Draw";
                    case AWAY_PLUS_ONE  -> "Away +1";
                };
            } else {
                r.displayLabel = "Match Result";
            }
        }
        return r;
    }

    public Long getId() { return id; }
    public BetType getBetType() { return betType; }
    public Long getMatchId() { return matchId; }
    public String getHomeTeamName() { return homeTeamName; }
    public String getAwayTeamName() { return awayTeamName; }
    public Prediction getPrediction() { return prediction; }
    public BetMarket getMarket() { return market; }
    public Integer getPredictedHomeGoals() { return predictedHomeGoals; }
    public Integer getPredictedAwayGoals() { return predictedAwayGoals; }
    public HandicapSelection getHandicapSelection() { return handicapSelection; }
    public Long getSelectedTeamId() { return selectedTeamId; }
    public String getSelectedTeamName() { return selectedTeamName; }
    public Long getSelectedPlayerId() { return selectedPlayerId; }
    public String getSelectedPlayerName() { return selectedPlayerName; }
    public String getDisplayLabel() { return displayLabel; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getOdds() { return odds; }
    public BigDecimal getTotalOdds() { return totalOdds; }
    public BigDecimal getPossibleWin() { return possibleWin; }
    public BetStatus getStatus() { return status; }
    public BigDecimal getProfit() { return profit; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public List<BetSelectionResponse> getSelections() { return selections; }
}

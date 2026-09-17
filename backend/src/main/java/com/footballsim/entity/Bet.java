package com.footballsim.entity;

import com.footballsim.enums.BetMarket;
import com.footballsim.enums.BetStatus;
import com.footballsim.enums.BetType;
import com.footballsim.enums.HandicapSelection;
import com.footballsim.enums.Prediction;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bets")
public class Bet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BetType betType = BetType.SINGLE;

    /** Only set for SINGLE bets; COMBO bets use {@link #selections} instead. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    /** Only set for SINGLE bets. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Prediction prediction;

    /** Distinguishes 1X2 bets (MATCH_RESULT) from exact-scoreline bets (CORRECT_SCORE). Combo bets are always MATCH_RESULT. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BetMarket market = BetMarket.MATCH_RESULT;

    /** Only set for SINGLE CORRECT_SCORE bets. */
    @Column(name = "predicted_home_goals")
    private Integer predictedHomeGoals;

    /** Only set for SINGLE CORRECT_SCORE bets. */
    @Column(name = "predicted_away_goals")
    private Integer predictedAwayGoals;

    /** Only set for SINGLE HANDICAP bets (European Handicap -1, home). */
    @Enumerated(EnumType.STRING)
    @Column(name = "handicap_selection", length = 20)
    private HandicapSelection handicapSelection;

    /** Only set for SINGLE CHAMPION bets. Null for all other markets. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_team_id")
    private Team selectedTeam;

    /** Only set for SINGLE TOP_SCORER bets. Null for all other markets. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_player_id")
    private Player selectedPlayer;

    @NotNull
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Odds snapshot for SINGLE bets; for COMBO bets see {@link #totalOdds}. */
    @Column(precision = 6, scale = 2)
    private BigDecimal odds;

    /** Product of selection odds snapshots; only set for COMBO bets. */
    @Column(name = "total_odds", precision = 10, scale = 2)
    private BigDecimal totalOdds;

    @NotNull
    @Column(name = "possible_win", nullable = false, precision = 19, scale = 2)
    private BigDecimal possibleWin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BetStatus status = BetStatus.OPEN;

    @OneToMany(mappedBy = "bet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BetSelection> selections = new ArrayList<>();

    @Column(precision = 19, scale = 2)
    private BigDecimal profit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public BetType getBetType() { return betType; }
    public void setBetType(BetType betType) { this.betType = betType; }

    public Match getMatch() { return match; }
    public void setMatch(Match match) { this.match = match; }

    public Prediction getPrediction() { return prediction; }
    public void setPrediction(Prediction prediction) { this.prediction = prediction; }

    public BetMarket getMarket() { return market; }
    public void setMarket(BetMarket market) { this.market = market; }

    public Integer getPredictedHomeGoals() { return predictedHomeGoals; }
    public void setPredictedHomeGoals(Integer predictedHomeGoals) { this.predictedHomeGoals = predictedHomeGoals; }

    public Integer getPredictedAwayGoals() { return predictedAwayGoals; }
    public void setPredictedAwayGoals(Integer predictedAwayGoals) { this.predictedAwayGoals = predictedAwayGoals; }

    public HandicapSelection getHandicapSelection() { return handicapSelection; }
    public void setHandicapSelection(HandicapSelection handicapSelection) { this.handicapSelection = handicapSelection; }

    public Team getSelectedTeam() { return selectedTeam; }
    public void setSelectedTeam(Team selectedTeam) { this.selectedTeam = selectedTeam; }

    public Player getSelectedPlayer() { return selectedPlayer; }
    public void setSelectedPlayer(Player selectedPlayer) { this.selectedPlayer = selectedPlayer; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getOdds() { return odds; }
    public void setOdds(BigDecimal odds) { this.odds = odds; }

    public BigDecimal getTotalOdds() { return totalOdds; }
    public void setTotalOdds(BigDecimal totalOdds) { this.totalOdds = totalOdds; }

    public List<BetSelection> getSelections() { return selections; }
    public void setSelections(List<BetSelection> selections) { this.selections = selections; }

    public BigDecimal getPossibleWin() { return possibleWin; }
    public void setPossibleWin(BigDecimal possibleWin) { this.possibleWin = possibleWin; }

    public BetStatus getStatus() { return status; }
    public void setStatus(BetStatus status) { this.status = status; }

    public BigDecimal getProfit() { return profit; }
    public void setProfit(BigDecimal profit) { this.profit = profit; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }
}

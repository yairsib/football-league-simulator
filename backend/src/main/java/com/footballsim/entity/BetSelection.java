package com.footballsim.entity;

import com.footballsim.enums.Prediction;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Entity
@Table(name = "bet_selections")
public class BetSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bet_id", nullable = false)
    private Bet bet;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Prediction prediction;

    @NotNull
    @Column(name = "odds_snapshot", nullable = false, precision = 6, scale = 2)
    private BigDecimal oddsSnapshot;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Bet getBet() { return bet; }
    public void setBet(Bet bet) { this.bet = bet; }

    public Match getMatch() { return match; }
    public void setMatch(Match match) { this.match = match; }

    public Prediction getPrediction() { return prediction; }
    public void setPrediction(Prediction prediction) { this.prediction = prediction; }

    public BigDecimal getOddsSnapshot() { return oddsSnapshot; }
    public void setOddsSnapshot(BigDecimal oddsSnapshot) { this.oddsSnapshot = oddsSnapshot; }
}

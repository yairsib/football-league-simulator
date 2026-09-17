package com.footballsim.entity;

import com.footballsim.enums.MatchStatus;
import com.footballsim.enums.WeatherCondition;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @Column(name = "home_goals")
    private Integer homeGoals;

    @Column(name = "away_goals")
    private Integer awayGoals;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.SCHEDULED;

    @Column(name = "betting_open", nullable = false)
    private boolean bettingOpen = false;

    @Column(name = "home_odds", precision = 6, scale = 2)
    private BigDecimal homeOdds;

    @Column(name = "draw_odds", precision = 6, scale = 2)
    private BigDecimal drawOdds;

    @Column(name = "away_odds", precision = 6, scale = 2)
    private BigDecimal awayOdds;

    // European Handicap -1 market, frozen together with the 1X2 odds when betting opens.
    @Column(name = "handicap_home_minus_one_odds", precision = 6, scale = 2)
    private BigDecimal handicapHomeMinusOneOdds;

    @Column(name = "handicap_draw_odds", precision = 6, scale = 2)
    private BigDecimal handicapDrawOdds;

    @Column(name = "handicap_away_plus_one_odds", precision = 6, scale = 2)
    private BigDecimal handicapAwayPlusOneOdds;

    @Column(name = "match_date")
    private LocalDateTime matchDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "weather_condition", length = 20)
    private WeatherCondition weatherCondition;

    @Column(name = "weather_impact")
    private Integer weatherImpact;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Round getRound() { return round; }
    public void setRound(Round round) { this.round = round; }

    public Team getHomeTeam() { return homeTeam; }
    public void setHomeTeam(Team homeTeam) { this.homeTeam = homeTeam; }

    public Team getAwayTeam() { return awayTeam; }
    public void setAwayTeam(Team awayTeam) { this.awayTeam = awayTeam; }

    public Integer getHomeGoals() { return homeGoals; }
    public void setHomeGoals(Integer homeGoals) { this.homeGoals = homeGoals; }

    public Integer getAwayGoals() { return awayGoals; }
    public void setAwayGoals(Integer awayGoals) { this.awayGoals = awayGoals; }

    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }

    public boolean isBettingOpen() { return bettingOpen; }
    public void setBettingOpen(boolean bettingOpen) { this.bettingOpen = bettingOpen; }

    public BigDecimal getHomeOdds() { return homeOdds; }
    public void setHomeOdds(BigDecimal homeOdds) { this.homeOdds = homeOdds; }

    public BigDecimal getDrawOdds() { return drawOdds; }
    public void setDrawOdds(BigDecimal drawOdds) { this.drawOdds = drawOdds; }

    public BigDecimal getAwayOdds() { return awayOdds; }
    public void setAwayOdds(BigDecimal awayOdds) { this.awayOdds = awayOdds; }

    public BigDecimal getHandicapHomeMinusOneOdds() { return handicapHomeMinusOneOdds; }
    public void setHandicapHomeMinusOneOdds(BigDecimal v) { this.handicapHomeMinusOneOdds = v; }

    public BigDecimal getHandicapDrawOdds() { return handicapDrawOdds; }
    public void setHandicapDrawOdds(BigDecimal v) { this.handicapDrawOdds = v; }

    public BigDecimal getHandicapAwayPlusOneOdds() { return handicapAwayPlusOneOdds; }
    public void setHandicapAwayPlusOneOdds(BigDecimal v) { this.handicapAwayPlusOneOdds = v; }

    public LocalDateTime getMatchDate() { return matchDate; }
    public void setMatchDate(LocalDateTime matchDate) { this.matchDate = matchDate; }

    public WeatherCondition getWeatherCondition() { return weatherCondition; }
    public void setWeatherCondition(WeatherCondition weatherCondition) { this.weatherCondition = weatherCondition; }

    public Integer getWeatherImpact() { return weatherImpact; }
    public void setWeatherImpact(Integer weatherImpact) { this.weatherImpact = weatherImpact; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

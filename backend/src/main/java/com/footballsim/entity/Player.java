package com.footballsim.entity;

import com.footballsim.enums.PlayerStatus;
import com.footballsim.enums.Position;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @NotBlank
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Position position;

    @Column(name = "jersey_number")
    private Integer jerseyNumber;

    @Column(nullable = false)
    private int rating;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayerStatus status = PlayerStatus.FIT;

    @Column(name = "injury_description", length = 255)
    private String injuryDescription;

    @Column(name = "injured_until_round")
    private Integer injuredUntilRound;

    @Column(name = "injury_matches_remaining", nullable = false)
    private int injuryMatchesRemaining = 0;

    @Column(name = "injury_matches_total")
    private Integer injuryMatchesTotal = 0;

    @Column(nullable = false)
    private int goals = 0;

    @Column(nullable = false)
    private int assists = 0;

    @Column(name = "red_cards", nullable = false)
    private int redCards = 0;

    @Column(name = "suspension_matches_remaining", nullable = false)
    private int suspensionMatchesRemaining = 0;

    @Column(nullable = false)
    private boolean starter = false;

    @Column(nullable = false)
    private boolean substitute = false;

    @Column(name = "lineup_order")
    private Integer lineupOrder;

    @Column(name = "data_source", length = 100)
    private String dataSource;

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

    public Team getTeam() { return team; }
    public void setTeam(Team team) { this.team = team; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Position getPosition() { return position; }
    public void setPosition(Position position) { this.position = position; }

    public Integer getJerseyNumber() { return jerseyNumber; }
    public void setJerseyNumber(Integer jerseyNumber) { this.jerseyNumber = jerseyNumber; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public PlayerStatus getStatus() { return status; }
    public void setStatus(PlayerStatus status) { this.status = status; }

    public String getInjuryDescription() { return injuryDescription; }
    public void setInjuryDescription(String injuryDescription) { this.injuryDescription = injuryDescription; }

    public Integer getInjuredUntilRound() { return injuredUntilRound; }
    public void setInjuredUntilRound(Integer injuredUntilRound) { this.injuredUntilRound = injuredUntilRound; }

    public int getInjuryMatchesRemaining() { return injuryMatchesRemaining; }
    public void setInjuryMatchesRemaining(int injuryMatchesRemaining) { this.injuryMatchesRemaining = injuryMatchesRemaining; }

    public Integer getInjuryMatchesTotal() { return injuryMatchesTotal; }
    public void setInjuryMatchesTotal(Integer injuryMatchesTotal) { this.injuryMatchesTotal = injuryMatchesTotal; }

    public int getGoals() { return goals; }
    public void setGoals(int goals) { this.goals = goals; }

    public int getAssists() { return assists; }
    public void setAssists(int assists) { this.assists = assists; }

    public int getRedCards() { return redCards; }
    public void setRedCards(int redCards) { this.redCards = redCards; }

    public int getSuspensionMatchesRemaining() { return suspensionMatchesRemaining; }
    public void setSuspensionMatchesRemaining(int suspensionMatchesRemaining) { this.suspensionMatchesRemaining = suspensionMatchesRemaining; }

    public boolean isStarter() { return starter; }
    public void setStarter(boolean starter) { this.starter = starter; }

    public boolean isSubstitute() { return substitute; }
    public void setSubstitute(boolean substitute) { this.substitute = substitute; }

    public Integer getLineupOrder() { return lineupOrder; }
    public void setLineupOrder(Integer lineupOrder) { this.lineupOrder = lineupOrder; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

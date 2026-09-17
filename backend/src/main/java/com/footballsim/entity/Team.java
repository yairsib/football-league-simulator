package com.footballsim.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

@Entity
@Table(name = "teams", uniqueConstraints = {
        @UniqueConstraint(columnNames = "name")
})
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "skill_level", nullable = false)
    private int skillLevel;

    @Column(nullable = false)
    private int morale;

    /**
     * The team's actual starting skill for the current season: the league-data value plus a
     * small random variation, fixed at import time. Season Reset restores THIS value and never
     * rerolls it; only a fresh league import creates a new baseline.
     */
    @Column(name = "baseline_skill_level")
    private Integer baselineSkillLevel;

    /** Starting morale for the current season (from the league data); restored by Season Reset. */
    @Column(name = "baseline_morale")
    private Integer baselineMorale;

    @Column(nullable = false)
    private int injuries;

    @Column(nullable = false)
    private int played = 0;

    @Column(nullable = false)
    private int wins = 0;

    @Column(nullable = false)
    private int draws = 0;

    @Column(nullable = false)
    private int losses = 0;

    @Column(name = "goals_for", nullable = false)
    private int goalsFor = 0;

    @Column(name = "goals_against", nullable = false)
    private int goalsAgainst = 0;

    @Column(nullable = false)
    private int points = 0;

    @Column(name = "default_formation", nullable = false, length = 20)
    private String defaultFormation = "4-3-3";

    @Column(name = "display_name", length = 100)
    private String displayName;

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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSkillLevel() { return skillLevel; }
    public void setSkillLevel(int skillLevel) { this.skillLevel = skillLevel; }

    public int getMorale() { return morale; }
    public void setMorale(int morale) { this.morale = morale; }

    public Integer getBaselineSkillLevel() { return baselineSkillLevel; }
    public void setBaselineSkillLevel(Integer baselineSkillLevel) { this.baselineSkillLevel = baselineSkillLevel; }

    public Integer getBaselineMorale() { return baselineMorale; }
    public void setBaselineMorale(Integer baselineMorale) { this.baselineMorale = baselineMorale; }

    public int getInjuries() { return injuries; }
    public void setInjuries(int injuries) { this.injuries = injuries; }

    public int getPlayed() { return played; }
    public void setPlayed(int played) { this.played = played; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getDraws() { return draws; }
    public void setDraws(int draws) { this.draws = draws; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public int getGoalsFor() { return goalsFor; }
    public void setGoalsFor(int goalsFor) { this.goalsFor = goalsFor; }

    public int getGoalsAgainst() { return goalsAgainst; }
    public void setGoalsAgainst(int goalsAgainst) { this.goalsAgainst = goalsAgainst; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public String getDefaultFormation() { return defaultFormation; }
    public void setDefaultFormation(String defaultFormation) { this.defaultFormation = defaultFormation; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

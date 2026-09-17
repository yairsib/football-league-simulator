package com.footballsim.dto;

import java.math.BigDecimal;

public class ChampionOddsOption {

    private Long teamId;
    private String teamName;
    private int skillLevel;
    private BigDecimal odds;

    public ChampionOddsOption(Long teamId, String teamName, int skillLevel, BigDecimal odds) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.skillLevel = skillLevel;
        this.odds = odds;
    }

    public Long getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public int getSkillLevel() { return skillLevel; }
    public BigDecimal getOdds() { return odds; }
}

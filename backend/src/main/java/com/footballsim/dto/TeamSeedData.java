package com.footballsim.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps one team object inside the real-data JSON file (e.g. ligat-haal-2025-2026.json).
 * Field names mirror the documented JSON schema (see schema.md in resources/data).
 */
public class TeamSeedData {

    private String name;
    private String displayName;
    private int skillLevel;
    private int morale;
    private String defaultFormation;
    private String dataSource;
    private List<PlayerSeedData> players = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getSkillLevel() { return skillLevel; }
    public void setSkillLevel(int skillLevel) { this.skillLevel = skillLevel; }

    public int getMorale() { return morale; }
    public void setMorale(int morale) { this.morale = morale; }

    public String getDefaultFormation() { return defaultFormation; }
    public void setDefaultFormation(String defaultFormation) { this.defaultFormation = defaultFormation; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public List<PlayerSeedData> getPlayers() { return players; }
    public void setPlayers(List<PlayerSeedData> players) { this.players = players; }

    public boolean isExample() {
        return dataSource != null && dataSource.toUpperCase().startsWith("EXAMPLE");
    }
}

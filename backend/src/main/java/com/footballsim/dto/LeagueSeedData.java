package com.footballsim.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the top-level object of the real-data JSON file (e.g. ligat-haal-2025-2026.json).
 * Field names mirror the documented JSON schema (see schema.md in resources/data).
 */
public class LeagueSeedData {

    private String season;
    private String league;
    private List<String> sourceNotes = new ArrayList<>();
    private List<TeamSeedData> teams = new ArrayList<>();

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public String getLeague() { return league; }
    public void setLeague(String league) { this.league = league; }

    public List<String> getSourceNotes() { return sourceNotes; }
    public void setSourceNotes(List<String> sourceNotes) { this.sourceNotes = sourceNotes; }

    public List<TeamSeedData> getTeams() { return teams; }
    public void setTeams(List<TeamSeedData> teams) { this.teams = teams; }
}

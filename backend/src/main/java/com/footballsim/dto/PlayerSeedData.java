package com.footballsim.dto;

/**
 * Maps one player object inside the real-data JSON file (e.g. ligat-haal-2025-2026.json).
 * Field names mirror the documented JSON schema (see schema.md in resources/data).
 */
public class PlayerSeedData {

    private String fullName;
    private String position;
    private Integer jerseyNumber;
    private int rating;
    private String status;
    private String injuryDescription;
    private Integer injuredUntilRound;
    private int injuryMatchesRemaining;
    private Integer injuryMatchesTotal;
    private boolean starter;
    private boolean substitute;
    private Integer lineupOrder;
    private String dataSource;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public Integer getJerseyNumber() { return jerseyNumber; }
    public void setJerseyNumber(Integer jerseyNumber) { this.jerseyNumber = jerseyNumber; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getInjuryDescription() { return injuryDescription; }
    public void setInjuryDescription(String injuryDescription) { this.injuryDescription = injuryDescription; }

    public Integer getInjuredUntilRound() { return injuredUntilRound; }
    public void setInjuredUntilRound(Integer injuredUntilRound) { this.injuredUntilRound = injuredUntilRound; }

    public int getInjuryMatchesRemaining() { return injuryMatchesRemaining; }
    public void setInjuryMatchesRemaining(int injuryMatchesRemaining) { this.injuryMatchesRemaining = injuryMatchesRemaining; }

    public Integer getInjuryMatchesTotal() { return injuryMatchesTotal; }
    public void setInjuryMatchesTotal(Integer injuryMatchesTotal) { this.injuryMatchesTotal = injuryMatchesTotal; }

    public boolean isStarter() { return starter; }
    public void setStarter(boolean starter) { this.starter = starter; }

    public boolean isSubstitute() { return substitute; }
    public void setSubstitute(boolean substitute) { this.substitute = substitute; }

    public Integer getLineupOrder() { return lineupOrder; }
    public void setLineupOrder(Integer lineupOrder) { this.lineupOrder = lineupOrder; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public boolean isExample() {
        return dataSource != null && dataSource.toUpperCase().startsWith("EXAMPLE");
    }
}

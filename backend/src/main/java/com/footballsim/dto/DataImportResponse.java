package com.footballsim.dto;

import java.util.List;

/**
 * Summary returned after importing a league real-data JSON file.
 */
public class DataImportResponse {

    private int teamsProcessed;
    private int playersProcessed;
    private List<String> warnings;
    private List<String> errors;

    public static DataImportResponse of(int teamsProcessed, int playersProcessed,
                                         List<String> warnings, List<String> errors) {
        DataImportResponse r = new DataImportResponse();
        r.teamsProcessed = teamsProcessed;
        r.playersProcessed = playersProcessed;
        r.warnings = warnings;
        r.errors = errors;
        return r;
    }

    public int getTeamsProcessed() { return teamsProcessed; }
    public int getPlayersProcessed() { return playersProcessed; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getErrors() { return errors; }
}

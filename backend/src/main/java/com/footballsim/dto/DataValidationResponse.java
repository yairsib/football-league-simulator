package com.footballsim.dto;

import java.util.List;

/**
 * Result of validating a league real-data JSON file: whether it is valid
 * enough to import (no errors), plus the full list of errors and warnings.
 */
public class DataValidationResponse {

    private boolean valid;
    private List<String> errors;
    private List<String> warnings;
    private List<TeamSquadSummary> squadSummaries;

    public static DataValidationResponse of(boolean valid, List<String> errors, List<String> warnings,
                                             List<TeamSquadSummary> squadSummaries) {
        DataValidationResponse r = new DataValidationResponse();
        r.valid = valid;
        r.errors = errors;
        r.warnings = warnings;
        r.squadSummaries = squadSummaries;
        return r;
    }

    public boolean isValid() { return valid; }
    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    public List<TeamSquadSummary> getSquadSummaries() { return squadSummaries; }
}

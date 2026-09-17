package com.footballsim.dto;

import com.footballsim.enums.HandicapSelection;

import java.math.BigDecimal;

/** One selectable handicap outcome and its current odds, as returned by GET /api/matches/{matchId}/handicap-odds. */
public class HandicapOddsOption {

    private final HandicapSelection selection;
    private final String label;
    private final BigDecimal odds;

    public HandicapOddsOption(HandicapSelection selection, String label, BigDecimal odds) {
        this.selection = selection;
        this.label = label;
        this.odds = odds;
    }

    public HandicapSelection getSelection() { return selection; }
    public String getLabel() { return label; }
    public BigDecimal getOdds() { return odds; }
}

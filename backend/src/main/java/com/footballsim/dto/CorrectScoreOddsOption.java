package com.footballsim.dto;

import java.math.BigDecimal;

/** One selectable scoreline and its current odds, as returned by GET /api/matches/{matchId}/correct-score-odds. */
public class CorrectScoreOddsOption {

    private final int homeGoals;
    private final int awayGoals;
    private final BigDecimal odds;

    public CorrectScoreOddsOption(int homeGoals, int awayGoals, BigDecimal odds) {
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.odds = odds;
    }

    public int getHomeGoals() { return homeGoals; }
    public int getAwayGoals() { return awayGoals; }
    public BigDecimal getOdds() { return odds; }
}

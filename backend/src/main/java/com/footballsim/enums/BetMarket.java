package com.footballsim.enums;

/**
 * The kind of outcome a bet is wagered on, distinct from {@link BetType} (SINGLE/COMBO).
 * MATCH_RESULT covers 1X2 (HOME_WIN/DRAW/AWAY_WIN) bets, used by both single and combo bets.
 * CORRECT_SCORE covers exact-scoreline bets, single bets only.
 * HANDICAP covers European Handicap -1 (home) bets (HOME_MINUS_ONE/HANDICAP_DRAW/AWAY_PLUS_ONE), single bets only.
 * CHAMPION covers season-long "who wins the title" bets, single bets only, no match reference.
 * TOP_SCORER covers season-long "who scores most goals" bets, single bets only, no match reference.
 * Combo bets remain MATCH_RESULT-only. Season bets (CHAMPION/TOP_SCORER) are SINGLE-only.
 */
public enum BetMarket {
    MATCH_RESULT,
    CORRECT_SCORE,
    HANDICAP,
    CHAMPION,
    TOP_SCORER
}

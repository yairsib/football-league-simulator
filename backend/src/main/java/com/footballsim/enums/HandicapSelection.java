package com.footballsim.enums;

/**
 * The three outcomes of a European Handicap -1 (home) bet.
 * HOME_MINUS_ONE: home wins by 2+ goals (h-1 > a).
 * HANDICAP_DRAW:  home wins by exactly 1 (h-1 == a).
 * AWAY_PLUS_ONE:  draw, away win, or home fails to cover (h-1 < a).
 * No pushes — every bet settles WON or LOST.
 */
public enum HandicapSelection {
    HOME_MINUS_ONE,
    HANDICAP_DRAW,
    AWAY_PLUS_ONE
}

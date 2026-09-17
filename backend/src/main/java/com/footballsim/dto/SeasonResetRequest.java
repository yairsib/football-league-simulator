package com.footballsim.dto;

public class SeasonResetRequest {

    /**
     * Must be true — prevents accidental calls.
     */
    private boolean confirm;

    /**
     * When true (default): delete old matches/rounds and regenerate a fresh schedule
     * with new weather assignments. When false: reset existing matches/rounds in-place.
     */
    private boolean regenerateSchedule = true;

    /**
     * When true: reset every user's balance to the starting balance (1000) after
     * cleanup. When false (default): preserve balances; OPEN bets are refunded first
     * so no stake is lost.
     */
    private boolean resetUserBalances = false;

    public boolean isConfirm() { return confirm; }
    public void setConfirm(boolean confirm) { this.confirm = confirm; }

    public boolean isRegenerateSchedule() { return regenerateSchedule; }
    public void setRegenerateSchedule(boolean regenerateSchedule) { this.regenerateSchedule = regenerateSchedule; }

    public boolean isResetUserBalances() { return resetUserBalances; }
    public void setResetUserBalances(boolean resetUserBalances) { this.resetUserBalances = resetUserBalances; }
}

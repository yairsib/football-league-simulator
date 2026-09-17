package com.footballsim.dto;

public class SeasonBetStatusResponse {

    private boolean open;
    private String reason;

    public SeasonBetStatusResponse(boolean open, String reason) {
        this.open = open;
        this.reason = reason;
    }

    public boolean isOpen() { return open; }
    public String getReason() { return reason; }
}

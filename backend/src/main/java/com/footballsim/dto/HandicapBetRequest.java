package com.footballsim.dto;

import com.footballsim.enums.HandicapSelection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class HandicapBetRequest {

    @NotNull(message = "matchId is required")
    private Long matchId;

    @NotNull(message = "selection is required")
    private HandicapSelection selection;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public HandicapSelection getSelection() { return selection; }
    public void setSelection(HandicapSelection selection) { this.selection = selection; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}

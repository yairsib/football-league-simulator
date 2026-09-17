package com.footballsim.dto;

import com.footballsim.enums.HandicapSelection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class UpdateHandicapBetRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "selection is required")
    private HandicapSelection selection;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public HandicapSelection getSelection() { return selection; }
    public void setSelection(HandicapSelection selection) { this.selection = selection; }
}

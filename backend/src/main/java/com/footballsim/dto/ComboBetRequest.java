package com.footballsim.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public class ComboBetRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    @NotEmpty(message = "selections are required")
    @Valid
    private List<BetSelectionRequest> selections;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public List<BetSelectionRequest> getSelections() { return selections; }
    public void setSelections(List<BetSelectionRequest> selections) { this.selections = selections; }
}

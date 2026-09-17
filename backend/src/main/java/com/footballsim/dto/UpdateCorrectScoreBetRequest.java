package com.footballsim.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class UpdateCorrectScoreBetRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "homeGoals is required")
    @Min(value = 0, message = "homeGoals must be between 0 and 6")
    @Max(value = 6, message = "homeGoals must be between 0 and 6")
    private Integer homeGoals;

    @NotNull(message = "awayGoals is required")
    @Min(value = 0, message = "awayGoals must be between 0 and 6")
    @Max(value = 6, message = "awayGoals must be between 0 and 6")
    private Integer awayGoals;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Integer getHomeGoals() { return homeGoals; }
    public void setHomeGoals(Integer homeGoals) { this.homeGoals = homeGoals; }

    public Integer getAwayGoals() { return awayGoals; }
    public void setAwayGoals(Integer awayGoals) { this.awayGoals = awayGoals; }
}

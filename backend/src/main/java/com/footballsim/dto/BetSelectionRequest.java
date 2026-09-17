package com.footballsim.dto;

import com.footballsim.enums.Prediction;
import jakarta.validation.constraints.NotNull;

public class BetSelectionRequest {

    @NotNull(message = "matchId is required")
    private Long matchId;

    @NotNull(message = "prediction is required")
    private Prediction prediction;

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public Prediction getPrediction() { return prediction; }
    public void setPrediction(Prediction prediction) { this.prediction = prediction; }
}

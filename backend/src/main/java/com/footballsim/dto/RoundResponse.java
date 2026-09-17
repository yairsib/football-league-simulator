package com.footballsim.dto;

import com.footballsim.entity.Round;
import com.footballsim.enums.RoundStatus;

import java.time.LocalDateTime;

public class RoundResponse {

    private Long id;
    private int roundNumber;
    private RoundStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public static RoundResponse from(Round round) {
        RoundResponse r = new RoundResponse();
        r.id = round.getId();
        r.roundNumber = round.getRoundNumber();
        r.status = round.getStatus();
        r.startedAt = round.getStartedAt();
        r.finishedAt = round.getFinishedAt();
        return r;
    }

    public Long getId() { return id; }
    public int getRoundNumber() { return roundNumber; }
    public RoundStatus getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
}

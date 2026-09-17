package com.footballsim.dto;

import com.footballsim.enums.Position;
import com.footballsim.service.LineupService;

public class ReplacedPlayerInfo {

    private String originalPlayerName;
    private String replacementPlayerName;
    private String reason;
    private Position position;

    public static ReplacedPlayerInfo from(LineupService.Replacement replacement) {
        ReplacedPlayerInfo r = new ReplacedPlayerInfo();
        r.originalPlayerName = replacement.originalPlayerName;
        r.replacementPlayerName = replacement.replacementPlayerName;
        r.reason = replacement.reason;
        r.position = replacement.position;
        return r;
    }

    public String getOriginalPlayerName() { return originalPlayerName; }
    public String getReplacementPlayerName() { return replacementPlayerName; }
    public String getReason() { return reason; }
    public Position getPosition() { return position; }
}

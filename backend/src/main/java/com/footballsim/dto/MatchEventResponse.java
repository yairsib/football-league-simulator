package com.footballsim.dto;

import com.footballsim.entity.MatchEvent;
import com.footballsim.enums.MatchEventType;

public class MatchEventResponse {

    private Long id;
    private Long matchId;
    private Long teamId;
    private String teamName;
    private Long playerId;
    private String playerName;
    private Long assistPlayerId;
    private String assistPlayerName;
    private Long playerInId;
    private String playerInName;
    private Long playerOutId;
    private String playerOutName;
    private MatchEventType eventType;
    private int minute;
    private String description;

    public static MatchEventResponse from(MatchEvent event) {
        MatchEventResponse r = new MatchEventResponse();
        r.id = event.getId();
        r.matchId = event.getMatch().getId();
        r.teamId = event.getTeam().getId();
        r.teamName = event.getTeam().getName();
        if (event.getPlayer() != null) {
            r.playerId = event.getPlayer().getId();
            r.playerName = event.getPlayer().getFullName();
        }
        if (event.getAssistPlayer() != null) {
            r.assistPlayerId = event.getAssistPlayer().getId();
            r.assistPlayerName = event.getAssistPlayer().getFullName();
        }
        if (event.getPlayerIn() != null) {
            r.playerInId = event.getPlayerIn().getId();
            r.playerInName = event.getPlayerIn().getFullName();
        }
        if (event.getPlayerOut() != null) {
            r.playerOutId = event.getPlayerOut().getId();
            r.playerOutName = event.getPlayerOut().getFullName();
        }
        r.eventType = event.getEventType();
        r.minute = event.getMinute();
        r.description = event.getDescription();
        return r;
    }

    public Long getId() { return id; }
    public Long getMatchId() { return matchId; }
    public Long getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public Long getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public Long getAssistPlayerId() { return assistPlayerId; }
    public String getAssistPlayerName() { return assistPlayerName; }
    public Long getPlayerInId() { return playerInId; }
    public String getPlayerInName() { return playerInName; }
    public Long getPlayerOutId() { return playerOutId; }
    public String getPlayerOutName() { return playerOutName; }
    public MatchEventType getEventType() { return eventType; }
    public int getMinute() { return minute; }
    public String getDescription() { return description; }
}

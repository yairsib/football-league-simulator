package com.footballsim.service;

import com.footballsim.dto.MatchEventResponse;
import com.footballsim.dto.MatchLineupsResponse;
import com.footballsim.dto.PlayerResponse;
import com.footballsim.dto.PlayerStatsResponse;
import com.footballsim.dto.ReplacedPlayerInfo;
import com.footballsim.dto.TeamLineupResponse;
import com.footballsim.dto.TeamSquadResponse;
import com.footballsim.entity.Match;
import com.footballsim.entity.Player;
import com.footballsim.entity.Team;
import com.footballsim.repository.MatchEventRepository;
import com.footballsim.repository.MatchRepository;
import com.footballsim.repository.PlayerRepository;
import com.footballsim.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final MatchEventRepository matchEventRepository;
    private final LineupService lineupService;

    public PlayerService(PlayerRepository playerRepository,
                          TeamRepository teamRepository,
                          MatchRepository matchRepository,
                          MatchEventRepository matchEventRepository,
                          LineupService lineupService) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.matchEventRepository = matchEventRepository;
        this.lineupService = lineupService;
    }

    @Transactional(readOnly = true)
    public PlayerResponse getPlayer(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NoSuchElementException("Player not found: " + playerId));
        return PlayerResponse.from(player);
    }

    @Transactional(readOnly = true)
    public List<PlayerResponse> getTeamPlayerStats(Long teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new NoSuchElementException("Team not found: " + teamId);
        }
        return playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(teamId).stream()
                .map(PlayerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MatchEventResponse> getMatchEvents(Long matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new NoSuchElementException("Match not found: " + matchId);
        }
        return matchEventRepository.findByMatch_IdOrderByMinuteAscIdAsc(matchId).stream()
                .map(MatchEventResponse::from)
                .toList();
    }

    private static final Set<String> ALLOWED_LEADER_STATS = Set.of("goals", "assists", "redCards");

    @Transactional(readOnly = true)
    public List<PlayerStatsResponse> getLeaders(String stat, int limit) {
        if (stat == null || !ALLOWED_LEADER_STATS.contains(stat)) {
            throw new IllegalArgumentException("Invalid stat parameter. Allowed values: goals, assists, redCards");
        }

        Comparator<Player> comparator = switch (stat) {
            case "goals" -> Comparator.comparingInt(Player::getGoals).reversed()
                    .thenComparing(Comparator.comparingInt(Player::getAssists).reversed())
                    .thenComparing(Comparator.comparingInt(Player::getRating).reversed())
                    .thenComparing(Player::getFullName);
            case "assists" -> Comparator.comparingInt(Player::getAssists).reversed()
                    .thenComparing(Comparator.comparingInt(Player::getGoals).reversed())
                    .thenComparing(Comparator.comparingInt(Player::getRating).reversed())
                    .thenComparing(Player::getFullName);
            default -> Comparator.comparingInt(Player::getRedCards).reversed()
                    .thenComparing(Player::getFullName);
        };

        return playerRepository.findAll().stream()
                .sorted(comparator)
                .limit(limit)
                .map(PlayerStatsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamSquadResponse getSquad(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));

        List<PlayerResponse> players = playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(teamId).stream()
                .map(PlayerResponse::from)
                .toList();

        return TeamSquadResponse.of(team, players);
    }

    @Transactional(readOnly = true)
    public TeamLineupResponse getLineup(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));

        return buildLineup(team);
    }

    @Transactional(readOnly = true)
    public MatchLineupsResponse getMatchLineups(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new NoSuchElementException("Match not found: " + matchId));

        TeamLineupResponse home = buildLineup(match.getHomeTeam());
        TeamLineupResponse away = buildLineup(match.getAwayTeam());

        return MatchLineupsResponse.of(match, home, away);
    }

    private TeamLineupResponse buildLineup(Team team) {
        List<Player> players = playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(team.getId());

        LineupService.EffectiveLineup effective = lineupService.generateEffectiveLineup(players);

        List<PlayerResponse> starters = effective.starters.stream().map(PlayerResponse::from).toList();
        List<PlayerResponse> substitutes = effective.substitutes.stream().map(PlayerResponse::from).toList();
        List<PlayerResponse> unavailable = effective.unavailable.stream().map(PlayerResponse::from).toList();
        List<ReplacedPlayerInfo> replacedPlayers = effective.replacements.stream().map(ReplacedPlayerInfo::from).toList();

        return TeamLineupResponse.of(team, starters, substitutes, unavailable,
                effective.effectiveLineupGenerated, effective.warnings, replacedPlayers);
    }
}

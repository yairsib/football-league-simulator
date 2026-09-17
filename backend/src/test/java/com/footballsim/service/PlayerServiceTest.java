package com.footballsim.service;

import com.footballsim.dto.PlayerResponse;
import com.footballsim.dto.PlayerStatsResponse;
import com.footballsim.entity.Player;
import com.footballsim.entity.Team;
import com.footballsim.enums.Position;
import com.footballsim.repository.MatchEventRepository;
import com.footballsim.repository.MatchRepository;
import com.footballsim.repository.PlayerRepository;
import com.footballsim.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerServiceTest {

    private PlayerRepository playerRepository;
    private TeamRepository teamRepository;
    private PlayerService service;

    private static Team team(long id, String name) {
        Team t = new Team();
        t.setId(id);
        t.setName(name);
        return t;
    }

    private static Player player(long id, Team team, String name, int goals, int assists, int redCards, int rating) {
        Player p = new Player();
        p.setId(id);
        p.setTeam(team);
        p.setFullName(name);
        p.setPosition(Position.ST);
        p.setRating(rating);
        p.setGoals(goals);
        p.setAssists(assists);
        p.setRedCards(redCards);
        return p;
    }

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        teamRepository = mock(TeamRepository.class);
        MatchRepository matchRepository = mock(MatchRepository.class);
        MatchEventRepository matchEventRepository = mock(MatchEventRepository.class);
        LineupService lineupService = new LineupService();
        service = new PlayerService(playerRepository, teamRepository, matchRepository, matchEventRepository, lineupService);
    }

    @Test
    void leadersByGoalsAreSortedDescendingWithTieBreakers() {
        Team team = team(1L, "Team A");
        List<Player> players = List.of(
                player(1L, team, "Low Scorer", 2, 5, 0, 70),
                player(2L, team, "Top Scorer", 5, 1, 0, 75),
                player(3L, team, "Same Goals Higher Assists", 5, 3, 0, 75),
                player(4L, team, "Zero", 0, 0, 0, 60)
        );
        when(playerRepository.findAll()).thenReturn(players);

        List<PlayerStatsResponse> leaders = service.getLeaders("goals", 20);

        assertEquals(4, leaders.size());
        assertEquals("Same Goals Higher Assists", leaders.get(0).getFullName());
        assertEquals("Top Scorer", leaders.get(1).getFullName());
        assertEquals("Low Scorer", leaders.get(2).getFullName());
        assertEquals("Zero", leaders.get(3).getFullName());
    }

    @Test
    void leadersByAssistsAreSortedDescendingWithTieBreakers() {
        Team team = team(1L, "Team A");
        List<Player> players = List.of(
                player(1L, team, "Few Assists", 1, 2, 0, 70),
                player(2L, team, "Most Assists", 0, 6, 0, 65),
                player(3L, team, "Tied Assists Higher Goals", 4, 2, 0, 70)
        );
        when(playerRepository.findAll()).thenReturn(players);

        List<PlayerStatsResponse> leaders = service.getLeaders("assists", 20);

        assertEquals("Most Assists", leaders.get(0).getFullName());
        assertEquals("Tied Assists Higher Goals", leaders.get(1).getFullName());
        assertEquals("Few Assists", leaders.get(2).getFullName());
    }

    @Test
    void redCardsLeadersSortByCountThenName() {
        Team team = team(1L, "Team A");
        List<Player> players = List.of(
                player(1L, team, "Zara", 0, 0, 1, 70),
                player(2L, team, "Amir", 0, 0, 1, 70),
                player(3L, team, "Booked Twice", 0, 0, 2, 70),
                player(4L, team, "Clean", 0, 0, 0, 70)
        );
        when(playerRepository.findAll()).thenReturn(players);

        List<PlayerStatsResponse> leaders = service.getLeaders("redCards", 20);

        assertEquals("Booked Twice", leaders.get(0).getFullName());
        assertEquals("Amir", leaders.get(1).getFullName());
        assertEquals("Zara", leaders.get(2).getFullName());
        assertEquals("Clean", leaders.get(3).getFullName());
    }

    @Test
    void invalidStatParameterThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> service.getLeaders("ratings", 20));
        assertThrows(IllegalArgumentException.class, () -> service.getLeaders(null, 20));
    }

    @Test
    void leadersAreLimitedAndIncludeTeamInfo() {
        Team team = team(7L, "Maccabi Test");
        List<Player> players = List.of(
                player(1L, team, "A", 10, 0, 0, 80),
                player(2L, team, "B", 9, 0, 0, 80),
                player(3L, team, "C", 8, 0, 0, 80)
        );
        when(playerRepository.findAll()).thenReturn(players);

        List<PlayerStatsResponse> leaders = service.getLeaders("goals", 2);

        assertEquals(2, leaders.size());
        assertEquals(7L, leaders.get(0).getTeamId());
        assertEquals("Maccabi Test", leaders.get(0).getTeamName());
    }

    @Test
    void leadersDoNotCrashWhenAllStatsAreZero() {
        Team team = team(1L, "Team A");
        List<Player> players = List.of(
                player(1L, team, "Zed", 0, 0, 0, 70),
                player(2L, team, "Amir", 0, 0, 0, 75)
        );
        when(playerRepository.findAll()).thenReturn(players);

        List<PlayerStatsResponse> goalsLeaders = service.getLeaders("goals", 20);
        assertEquals(2, goalsLeaders.size());
        // sorted by rating desc then name asc when all goals are equal
        assertEquals("Amir", goalsLeaders.get(0).getFullName());
        assertEquals("Zed", goalsLeaders.get(1).getFullName());
    }

    @Test
    void teamPlayerStatsReturnsAllPlayersForTeam() {
        Team team = team(3L, "Team C");
        when(teamRepository.existsById(3L)).thenReturn(true);
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(3L)).thenReturn(List.of(
                player(1L, team, "Player One", 1, 1, 0, 70),
                player(2L, team, "Player Two", 0, 0, 0, 65)
        ));

        List<PlayerResponse> result = service.getTeamPlayerStats(3L);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(p -> p.getFullName().equals("Player One")));
        assertTrue(result.stream().anyMatch(p -> p.getFullName().equals("Player Two")));
    }
}

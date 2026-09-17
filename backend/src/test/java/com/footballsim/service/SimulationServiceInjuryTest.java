package com.footballsim.service;

import com.footballsim.entity.Player;
import com.footballsim.entity.Team;
import com.footballsim.enums.PlayerStatus;
import com.footballsim.enums.Position;
import com.footballsim.repository.PlayerRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the simulation-only injury countdown/recovery in SimulationService.
 * Injury *generation* is random (10% per team per match), so these tests focus on the
 * deterministic part: progressing and clearing an existing injury.
 */
class SimulationServiceInjuryTest {

    /** A Random whose nextDouble() always returns 1.0 — guarantees the 10% new-injury roll never fires. */
    private static final class NeverInjuringRandom extends Random {
        @Override public double nextDouble() { return 1.0; }
    }

    /**
     * progressTeamInjuries() touches only the PlayerRepository, so OddsService/BetService/
     * MatchRepository/RoundRepository/MatchEventRepository are passed as null rather than mocked —
     * mocking concrete service classes requires Mockito's inline mock maker, which doesn't yet
     * support this JDK.
     */
    private static SimulationService newService(PlayerRepository playerRepository) {
        SimulationService service = new SimulationService(null, null, playerRepository, null, null, null, new LineupService());
        service.setRandom(new NeverInjuringRandom());
        return service;
    }

    private static Player player(String name, PlayerStatus status, int remaining, Integer total) {
        Player p = new Player();
        p.setFullName(name);
        p.setPosition(Position.CM);
        p.setRating(70);
        p.setStatus(status);
        p.setInjuryMatchesRemaining(remaining);
        p.setInjuryMatchesTotal(total);
        p.setStarter(true);
        return p;
    }

    @Test
    void playerRecoversWhenInjuryCountdownReachesZero() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        Team team = new Team();
        team.setId(1L);
        team.setName("Test FC");

        Player recovering = player("Recovering Guy", PlayerStatus.INJURED, 1, 1);
        recovering.setInjuryDescription("Knock");

        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(1L)).thenReturn(List.of(recovering));

        newService(playerRepository).progressTeamInjuries(team);

        assertThat(recovering.getStatus()).isEqualTo(PlayerStatus.FIT);
        assertThat(recovering.getInjuryDescription()).isNull();
        assertThat(recovering.getInjuryMatchesRemaining()).isZero();
        assertThat(recovering.getInjuryMatchesTotal()).isZero();
    }

    @Test
    void playerStaysInjuredAndCountsDownWhenMatchesRemain() {
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        Team team = new Team();
        team.setId(2L);
        team.setName("Test FC");

        Player stillOut = player("Still Out Guy", PlayerStatus.INJURED, 3, 5);
        stillOut.setInjuryDescription("Hamstring injury");

        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(2L)).thenReturn(List.of(stillOut));

        newService(playerRepository).progressTeamInjuries(team);

        assertThat(stillOut.getStatus()).isEqualTo(PlayerStatus.INJURED);
        assertThat(stillOut.getInjuryMatchesRemaining()).isEqualTo(2);
        assertThat(stillOut.getInjuryMatchesTotal()).isEqualTo(5);
        assertThat(stillOut.getInjuryDescription()).isEqualTo("Hamstring injury");
    }
}

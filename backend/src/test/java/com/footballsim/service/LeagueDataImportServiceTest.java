package com.footballsim.service;

import com.footballsim.dto.DataImportResponse;
import com.footballsim.dto.LeagueSeedData;
import com.footballsim.dto.TeamSeedData;
import com.footballsim.entity.Player;
import com.footballsim.entity.Team;
import com.footballsim.enums.MatchStatus;
import com.footballsim.enums.RoundStatus;
import com.footballsim.repository.BetRepository;
import com.footballsim.repository.MatchEventRepository;
import com.footballsim.repository.MatchRepository;
import com.footballsim.repository.PlayerRepository;
import com.footballsim.repository.RoundRepository;
import com.footballsim.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Milestone 41: randomised initial skill around the league-data value, persisted as the season
 * baseline, and the pre-season-only import rule. Runs against the real bundled JSON.
 */
@ExtendWith(MockitoExtension.class)
class LeagueDataImportServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private RoundRepository roundRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private BetRepository betRepository;
    @Mock private MatchEventRepository matchEventRepository;

    private LeagueDataImportService service;

    @BeforeEach
    void setUp() {
        service = new LeagueDataImportService(teamRepository, playerRepository,
                roundRepository, matchRepository, betRepository, matchEventRepository);
    }

    private void stubPreSeason() {
        lenient().when(roundRepository.existsByStatusNot(RoundStatus.NOT_STARTED)).thenReturn(false);
        lenient().when(matchRepository.existsByStatusNot(MatchStatus.SCHEDULED)).thenReturn(false);
        lenient().when(matchEventRepository.count()).thenReturn(0L);
        lenient().when(betRepository.count()).thenReturn(0L);
    }

    private void stubFreshImportTargets() {
        lenient().when(teamRepository.findByName(anyString())).thenReturn(Optional.empty());
        lenient().when(teamRepository.save(any(Team.class))).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            if (t.getId() == null) t.setId((long) (t.getName().hashCode() & 0xffff));
            return t;
        });
        lenient().when(playerRepository.findByTeam_IdAndFullName(any(), anyString())).thenReturn(Optional.empty());
        lenient().when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Map<String, Team> importAndCaptureTeams() {
        DataImportResponse resp = service.importData();
        assertThat(resp.getErrors()).isEmpty();
        assertThat(resp.getTeamsProcessed()).isEqualTo(14);
        ArgumentCaptor<Team> captor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository, times(14)).save(captor.capture());
        Map<String, Team> byName = new HashMap<>();
        for (Team t : captor.getAllValues()) byName.put(t.getName(), t);
        return byName;
    }

    @Test
    void import_assignsRandomisedInitialSkillWithinNarrowBand_andPersistsItAsBaseline() {
        stubPreSeason();
        stubFreshImportTargets();
        service.setRandom(new Random(42));

        LeagueSeedData seed = service.loadFromClasspath();
        Map<String, Team> teams = importAndCaptureTeams();

        int nonZeroOffsets = 0;
        for (TeamSeedData ts : seed.getTeams()) {
            Team t = teams.get(ts.getName());
            assertThat(t).as(ts.getName()).isNotNull();
            int offset = t.getSkillLevel() - ts.getSkillLevel();
            assertThat(Math.abs(offset)).as("offset for %s", ts.getName())
                    .isLessThanOrEqualTo(LeagueDataImportService.INITIAL_SKILL_VARIATION);
            assertThat(t.getSkillLevel()).isBetween(40, 100);
            // persisted baseline == the generated starting skill (not the raw JSON number)
            assertThat(t.getBaselineSkillLevel()).isEqualTo(t.getSkillLevel());
            assertThat(t.getMorale()).isEqualTo(ts.getMorale());
            assertThat(t.getBaselineMorale()).isEqualTo(ts.getMorale());
            if (offset != 0) nonZeroOffsets++;
        }
        assertThat(teams).hasSize(14);
        // it really is randomised: with seed 42 several teams differ from the JSON value
        assertThat(nonZeroOffsets).isGreaterThan(3);
    }

    @Test
    void import_clampsToGlobalSkillRange_evenAtTheEdges() {
        stubPreSeason();
        assertThat(service.randomisedInitialSkill(100)).isBetween(97, 100);
        assertThat(service.randomisedInitialSkill(40)).isBetween(40, 43);
        for (int i = 0; i < 200; i++) {
            int s = service.randomisedInitialSkill(63);
            assertThat(s).isBetween(60, 66);
        }
    }

    @Test
    void import_producesDifferentStartingSkillsAcrossFreshSeeds_thenCurrentSkillMayDrift() {
        stubPreSeason();
        stubFreshImportTargets();

        service.setRandom(new Random(1));
        Map<String, Team> first = importAndCaptureTeams();
        reset(teamRepository);
        stubFreshImportTargets();
        service.setRandom(new Random(2));
        Map<String, Team> second = importAndCaptureTeams();

        long differing = first.keySet().stream()
                .filter(n -> first.get(n).getSkillLevel() != second.get(n).getSkillLevel())
                .count();
        assertThat(differing).isGreaterThan(0);

        // current skill can drift independently of the persisted baseline
        Team t = first.get("Hapoel Beer Sheva");
        int baseline = t.getBaselineSkillLevel();
        t.setSkillLevel(Math.min(100, t.getSkillLevel() + 5));
        assertThat(t.getSkillLevel()).isNotEqualTo(baseline);
        assertThat(t.getBaselineSkillLevel()).isEqualTo(baseline);
    }

    @Test
    void import_isRejected_afterBettingHasOpened_andChangesNothing() {
        when(roundRepository.existsByStatusNot(RoundStatus.NOT_STARTED)).thenReturn(true);

        assertThatThrownBy(() -> service.importData())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before the season starts")
                .hasMessageContaining("Reset Season");

        verifyNoInteractions(teamRepository, playerRepository);
    }

    @Test
    void import_isRejected_afterAMatchHasFinished() {
        when(roundRepository.existsByStatusNot(RoundStatus.NOT_STARTED)).thenReturn(false);
        when(matchRepository.existsByStatusNot(MatchStatus.SCHEDULED)).thenReturn(true);

        assertThatThrownBy(() -> service.importData()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(teamRepository, playerRepository);
    }

    @Test
    void import_isRejected_whenEventsOrBetsExist() {
        when(roundRepository.existsByStatusNot(RoundStatus.NOT_STARTED)).thenReturn(false);
        when(matchRepository.existsByStatusNot(MatchStatus.SCHEDULED)).thenReturn(false);
        when(matchEventRepository.count()).thenReturn(3L);
        assertThatThrownBy(() -> service.importData()).isInstanceOf(IllegalStateException.class);

        when(matchEventRepository.count()).thenReturn(0L);
        when(betRepository.count()).thenReturn(1L);
        assertThatThrownBy(() -> service.importData()).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(teamRepository, playerRepository);
        assertThat(service.isSeasonStarted()).isTrue();
    }

    @Test
    void import_isAllowed_inPreSeason_includingAfterAProperReset() {
        stubPreSeason();
        stubFreshImportTargets();
        assertThat(service.isSeasonStarted()).isFalse();
        DataImportResponse resp = service.importData();
        assertThat(resp.getErrors()).isEmpty();
        assertThat(resp.getTeamsProcessed()).isEqualTo(14);
        assertThat(resp.getPlayersProcessed()).isGreaterThan(300);
    }

    @Test
    void validate_isNotBlockedBySeasonState() {
        when(roundRepository.existsByStatusNot(RoundStatus.NOT_STARTED)).thenReturn(true);
        assertThat(service.isSeasonStarted()).isTrue();
        assertThat(service.validate().isValid()).isTrue();   // read-only validation stays available
        verifyNoInteractions(teamRepository, playerRepository);
    }
}

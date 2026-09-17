package com.footballsim.service;

import com.footballsim.dto.HandicapOddsOption;
import com.footballsim.entity.Match;
import com.footballsim.entity.Round;
import com.footballsim.entity.Team;
import com.footballsim.enums.HandicapSelection;
import com.footballsim.enums.MatchStatus;
import com.footballsim.enums.RoundStatus;
import com.footballsim.enums.WeatherCondition;
import com.footballsim.repository.MatchRepository;
import com.footballsim.repository.PlayerRepository;
import com.footballsim.repository.RoundRepository;
import com.footballsim.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Milestone 41: the handicap market is priced ONCE (from the same probability vector as the 1X2
 * odds) when betting opens, stored on the match, served unchanged by MatchService and booked at
 * exactly that price by BetService.
 */
class SimulationServiceMarketFreezeTest {

    private MatchRepository matchRepository;
    private RoundRepository roundRepository;
    private OddsService oddsService;
    private SimulationService simulationService;
    private MatchService matchService;

    @BeforeEach
    void setUp() {
        matchRepository = mock(MatchRepository.class);
        roundRepository = mock(RoundRepository.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(anyLong())).thenReturn(List.of());
        oddsService = new OddsService(playerRepository, teamRepository);
        simulationService = new SimulationService(matchRepository, roundRepository, playerRepository,
                null, oddsService, null, new LineupService());
        matchService = new MatchService(matchRepository, oddsService);
    }

    private static Team team(long id, int skill) {
        Team t = new Team();
        t.setId(id);
        t.setName("Team " + id);
        t.setSkillLevel(skill);
        t.setMorale(5);
        return t;
    }

    private static Match match(long id, Round round, Team home, Team away, WeatherCondition weather, int impact) {
        Match m = new Match();
        m.setId(id);
        m.setRound(round);
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        m.setStatus(MatchStatus.SCHEDULED);
        m.setWeatherCondition(weather);
        m.setWeatherImpact(impact);
        return m;
    }

    @Test
    void openBetting_freezesOneXTwoAndHandicapTogether_coherentAndSane() {
        Round round = new Round();
        round.setRoundNumber(1);
        round.setStatus(RoundStatus.NOT_STARTED);
        when(roundRepository.findByRoundNumber(1)).thenReturn(Optional.of(round));

        // WIND/STORM/COLD are the jittered conditions that used to make live prices wander.
        Match m1 = match(1L, round, team(1, 92), team(2, 63), WeatherCondition.WIND, 0);
        Match m2 = match(2L, round, team(3, 70), team(4, 90), WeatherCondition.STORM, -2);
        Match m3 = match(3L, round, team(5, 78), team(6, 78), WeatherCondition.COLD, 1);
        Match m4 = match(4L, round, team(7, 85), team(8, 74), WeatherCondition.CLEAR, 0);
        when(matchRepository.findByRound_RoundNumberOrderByIdAsc(1)).thenReturn(List.of(m1, m2, m3, m4));

        simulationService.openBettingForRound(1);

        for (Match m : List.of(m1, m2, m3, m4)) {
            assertThat(m.getStatus()).isEqualTo(MatchStatus.BETTING_OPEN);
            for (BigDecimal odds : List.of(m.getHomeOdds(), m.getDrawOdds(), m.getAwayOdds(),
                    m.getHandicapHomeMinusOneOdds(), m.getHandicapDrawOdds(), m.getHandicapAwayPlusOneOdds())) {
                assertThat(odds).isNotNull();
                assertThat(odds).isGreaterThan(BigDecimal.ONE);
                assertThat(odds.scale()).isEqualTo(2);
            }
            // Milestone 37 invariant, now guaranteed on the STORED prices: Home -1 is harder than Home Win
            assertThat(m.getHandicapHomeMinusOneOdds()).isGreaterThan(m.getHomeOdds());
            // Away +1 (draw or away win) is easier than Away Win
            assertThat(m.getHandicapAwayPlusOneOdds()).isLessThan(m.getAwayOdds());
        }
        // strong home favourite prices short; the away-favourite match prices the home side long
        assertThat(m1.getHomeOdds()).isLessThan(m2.getHomeOdds());
    }

    @Test
    void handicapEndpoint_returnsIdenticalStoredPricesOnEveryCall_andPlacementUsesThem() {
        Round round = new Round();
        round.setRoundNumber(1);
        Match m = match(5L, round, team(1, 92), team(2, 63), WeatherCondition.STORM, -3);
        m.setStatus(MatchStatus.BETTING_OPEN);
        m.setHomeOdds(new BigDecimal("1.41"));
        m.setDrawOdds(new BigDecimal("4.20"));
        m.setAwayOdds(new BigDecimal("9.50"));
        m.setHandicapHomeMinusOneOdds(new BigDecimal("1.77"));
        m.setHandicapDrawOdds(new BigDecimal("4.60"));
        m.setHandicapAwayPlusOneOdds(new BigDecimal("2.05"));
        when(matchRepository.findById(5L)).thenReturn(Optional.of(m));

        List<HandicapOddsOption> first = matchService.getHandicapOddsOptions(5L);
        for (int i = 0; i < 25; i++) {
            List<HandicapOddsOption> again = matchService.getHandicapOddsOptions(5L);
            for (int k = 0; k < 3; k++) {
                assertThat(again.get(k).getSelection()).isEqualTo(first.get(k).getSelection());
                assertThat(again.get(k).getOdds()).isEqualByComparingTo(first.get(k).getOdds());
            }
        }
        assertThat(first.get(0).getOdds()).isEqualByComparingTo("1.77");
        assertThat(first.get(1).getOdds()).isEqualByComparingTo("4.60");
        assertThat(first.get(2).getOdds()).isEqualByComparingTo("2.05");
        assertThat(first.get(0).getLabel()).isEqualTo("Home -1");

        // what BetService books = what the endpoint displayed
        assertThat(BetService.frozenHandicapOdds(m, HandicapSelection.HOME_MINUS_ONE)).isEqualByComparingTo("1.77");
        assertThat(BetService.frozenHandicapOdds(m, HandicapSelection.HANDICAP_DRAW)).isEqualByComparingTo("4.60");
        assertThat(BetService.frozenHandicapOdds(m, HandicapSelection.AWAY_PLUS_ONE)).isEqualByComparingTo("2.05");
    }

    @Test
    void handicapMarket_isUnavailableUntilBettingOpens() {
        Round round = new Round();
        round.setRoundNumber(2);
        Match m = match(6L, round, team(1, 80), team(2, 70), WeatherCondition.CLEAR, 0);   // SCHEDULED, no frozen prices
        when(matchRepository.findById(6L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> matchService.getHandicapOddsOptions(6L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("once betting opens");
        assertThatThrownBy(() -> BetService.frozenHandicapOdds(m, HandicapSelection.HOME_MINUS_ONE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void priceMatchMarkets_homeMinusOneAlwaysLongerThanHomeWin_acrossMatchupsAndWeather() {
        Round round = new Round();
        round.setRoundNumber(1);
        int[][] matchups = {{92, 63}, {63, 92}, {78, 78}, {85, 81}, {70, 88}, {100, 40}};
        for (WeatherCondition w : WeatherCondition.values()) {
            for (int[] mu : matchups) {
                for (int rep = 0; rep < 5; rep++) {
                    Match m = match(1L, round, team(1, mu[0]), team(2, mu[1]), w, -1);
                    OddsService.MatchMarketOdds odds = oddsService.priceMatchMarkets(m);
                    assertThat(odds.handicapHomeMinusOne).as("%s %s", w, mu).isGreaterThan(odds.home);
                    assertThat(odds.handicapAwayPlusOne).as("%s %s", w, mu).isLessThan(odds.away);
                    assertThat(odds.home).isGreaterThan(BigDecimal.ONE);
                    assertThat(odds.handicapDraw).isGreaterThan(BigDecimal.ONE);
                }
            }
        }
    }
}

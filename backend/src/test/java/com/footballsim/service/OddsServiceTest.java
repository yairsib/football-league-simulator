package com.footballsim.service;

import com.footballsim.dto.ChampionOddsOption;
import com.footballsim.dto.CorrectScoreOddsOption;
import com.footballsim.dto.HandicapOddsOption;
import com.footballsim.dto.TopScorerOddsOption;
import com.footballsim.entity.Match;
import com.footballsim.entity.Player;
import com.footballsim.entity.Team;
import com.footballsim.enums.HandicapSelection;
import com.footballsim.enums.Position;
import com.footballsim.enums.WeatherCondition;
import com.footballsim.repository.PlayerRepository;
import com.footballsim.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the probability-based odds model in OddsService (Milestone 19).
 * No squad data is mocked in (PlayerRepository returns empty lists), so the model
 * exercises only its skill/morale/home-advantage/injuries/weather components —
 * the same inputs every team has regardless of whether real squad data exists.
 */
class OddsServiceTest {

    private OddsService oddsService;

    private PlayerRepository playerRepository;
    private TeamRepository teamRepository;

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        teamRepository = mock(TeamRepository.class);
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(anyLong())).thenReturn(List.of());
        oddsService = new OddsService(playerRepository, teamRepository);
    }

    private static Team team(long id, String name, int skillLevel, int morale, int injuries) {
        Team t = new Team();
        t.setId(id);
        t.setName(name);
        t.setSkillLevel(skillLevel);
        t.setMorale(morale);
        t.setInjuries(injuries);
        return t;
    }

    private double toDouble(BigDecimal value) {
        return value.doubleValue();
    }

    private static Match match(Team home, Team away) {
        Match m = new Match();
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        return m;
    }

    @Test
    void strongHomeFavoriteReceivesLowHomeOdds_exampleA() {
        Team beerSheva = team(1L, "Hapoel Beer Sheva", 92, 5, 0);
        Team bneiReineh = team(2L, "Maccabi Bnei Reineh", 63, 5, 0);

        BigDecimal[] odds = oddsService.computeOdds(beerSheva, bneiReineh, WeatherCondition.CLEAR, 0);

        assertThat(toDouble(odds[0])).as("home odds").isBetween(1.25, 1.60);
        assertThat(toDouble(odds[1])).as("draw odds").isBetween(3.80, 5.50);
        assertThat(toDouble(odds[2])).as("away odds").isBetween(6.00, 12.50);
    }

    @Test
    void closeTeamsHaveBalancedOdds_exampleB() {
        Team netanya = team(1L, "Maccabi Netanya", 78, 5, 0);
        Team sakhnin = team(2L, "Bnei Sakhnin", 76, 5, 0);

        BigDecimal[] odds = oddsService.computeOdds(netanya, sakhnin, WeatherCondition.CLEAR, 0);

        assertThat(toDouble(odds[0])).as("home odds").isBetween(2.10, 2.80);
        assertThat(toDouble(odds[1])).as("draw odds").isBetween(3.00, 3.80);
        assertThat(toDouble(odds[2])).as("away odds").isBetween(2.50, 3.50);
    }

    @Test
    void strongAwayFavoriteReceivesLowAwayOdds_exampleC() {
        Team tiberias = team(1L, "Ironi Tiberias", 68, 5, 0);
        Team telAviv = team(2L, "Maccabi Tel Aviv", 91, 5, 0);

        BigDecimal[] odds = oddsService.computeOdds(tiberias, telAviv, WeatherCondition.CLEAR, 0);

        assertThat(toDouble(odds[2])).as("away odds").isBetween(1.40, 1.90);
        assertThat(toDouble(odds[0])).as("home odds").isGreaterThan(toDouble(odds[2]));
    }

    @Test
    void favoriteReceivesLowerOddsThanUnderdog() {
        Team strong = team(1L, "Strong FC", 95, 8, 0);
        Team weak = team(2L, "Weak FC", 45, 2, 0);

        BigDecimal[] odds = oddsService.computeOdds(strong, weak, WeatherCondition.CLEAR, 0);

        assertThat(toDouble(odds[0])).isLessThan(toDouble(odds[1]));
        assertThat(toDouble(odds[1])).isLessThan(toDouble(odds[2]));
    }

    @Test
    void evenlyMatchedTeamsProduceCloseHomeAndAwayOdds() {
        Team home = team(1L, "Even FC", 75, 5, 0);
        Team away = team(2L, "Steven FC", 75, 5, 0);

        BigDecimal[] odds = oddsService.computeOdds(home, away, WeatherCondition.CLEAR, 0);

        assertThat(Math.abs(toDouble(odds[0]) - toDouble(odds[2]))).isLessThan(1.0);
    }

    @Test
    void drawOddsStayInRealisticRange() {
        Team a = team(1L, "Team A", 80, 5, 0);
        Team b = team(2L, "Team B", 70, 5, 0);

        BigDecimal[] odds = oddsService.computeOdds(a, b, WeatherCondition.CLEAR, 0);

        assertThat(toDouble(odds[1])).isBetween(2.50, 7.00);
    }

    @Test
    void oddsAreRoundedToTwoDecimals() {
        Team a = team(1L, "Team A", 88, 6, 1);
        Team b = team(2L, "Team B", 67, 4, 2);

        for (BigDecimal odd : oddsService.computeOdds(a, b, WeatherCondition.RAIN, -1)) {
            assertThat(odd.scale()).isEqualTo(2);
        }
    }

    @Test
    void oddsNeverGoBelowMinimumOrAboveMaximum() {
        Team extremelyStrong = team(1L, "Titan FC", 100, 10, 0);
        Team extremelyWeak = team(2L, "Minnow FC", 40, 0, 5);

        BigDecimal[] odds = oddsService.computeOdds(extremelyStrong, extremelyWeak, WeatherCondition.STORM, -2);

        for (BigDecimal odd : odds) {
            assertThat(toDouble(odd)).isGreaterThanOrEqualTo(1.20);
            assertThat(toDouble(odd)).isLessThanOrEqualTo(15.00);
        }
    }

    @Test
    void bookmakerMarginIsApplied() {
        Team a = team(1L, "Team A", 80, 5, 0);
        Team b = team(2L, "Team B", 75, 5, 0);

        BigDecimal[] odds = oddsService.computeOdds(a, b, WeatherCondition.CLEAR, 0);

        double impliedProbabilitySum = 1.0 / toDouble(odds[0]) + 1.0 / toDouble(odds[1]) + 1.0 / toDouble(odds[2]);
        assertThat(impliedProbabilitySum).isGreaterThan(1.0);
    }

    @Test
    void oddsAreNeverNaNInfiniteOrZero() {
        Team a = team(1L, "Team A", 92, 5, 0);
        Team b = team(2L, "Team B", 63, 5, 0);

        for (WeatherCondition condition : WeatherCondition.values()) {
            for (BigDecimal odd : oddsService.computeOdds(a, b, condition, 1)) {
                double value = toDouble(odd);
                assertThat(Double.isNaN(value)).isFalse();
                assertThat(Double.isInfinite(value)).isFalse();
                assertThat(value).isGreaterThan(0.0);
            }
        }
    }

    @Test
    void probabilitiesAreValidAndSumToOne() {
        Team a = team(1L, "Team A", 85, 6, 1);
        Team b = team(2L, "Team B", 72, 4, 0);

        double[] probs = oddsService.computeProbabilities(a, b, WeatherCondition.WIND, 1);

        assertThat(probs).hasSize(3);
        for (double p : probs) {
            assertThat(p).isGreaterThan(0.0).isLessThan(1.0);
        }
        assertThat(probs[0] + probs[1] + probs[2]).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    // ─── Correct score odds (Milestone 24) ─────────────────────────────────

    @Test
    void correctScoreOdds_areWithinBoundsRoundedAndNeverNaNOrInfinite() {
        Team a = team(1L, "Team A", 80, 5, 0);
        Team b = team(2L, "Team B", 75, 5, 0);
        Match m = match(a, b);

        for (int home = 0; home <= 4; home++) {
            for (int away = 0; away <= 4; away++) {
                BigDecimal odds = oddsService.computeCorrectScoreOdds(m, home, away);
                assertThat(odds.scale()).isEqualTo(2);
                assertThat(Double.isNaN(toDouble(odds))).isFalse();
                assertThat(Double.isInfinite(toDouble(odds))).isFalse();
                assertThat(toDouble(odds)).isGreaterThanOrEqualTo(4.00);
                assertThat(toDouble(odds)).isLessThanOrEqualTo(80.00);
            }
        }
    }

    @Test
    void getPopularCorrectScores_returns25OptionsCoveringZeroToFour() {
        Team a = team(1L, "Team A", 80, 5, 0);
        Team b = team(2L, "Team B", 75, 5, 0);
        Match m = match(a, b);

        List<CorrectScoreOddsOption> options = oddsService.getPopularCorrectScores(m);

        assertThat(options).hasSize(25);
        assertThat(options.get(0).getHomeGoals()).isEqualTo(0);
        assertThat(options.get(0).getAwayGoals()).isEqualTo(0);
        assertThat(options.get(24).getHomeGoals()).isEqualTo(4);
        assertThat(options.get(24).getAwayGoals()).isEqualTo(4);
        for (CorrectScoreOddsOption option : options) {
            assertThat(toDouble(option.getOdds())).isGreaterThanOrEqualTo(4.00);
            assertThat(toDouble(option.getOdds())).isLessThanOrEqualTo(80.00);
        }
    }

    @Test
    void commonScoresHaveLowerOddsThanRareScores() {
        Team a = team(1L, "Team A", 80, 5, 0);
        Team b = team(2L, "Team B", 78, 5, 0);
        Match m = match(a, b);

        BigDecimal oneNil = oddsService.computeCorrectScoreOdds(m, 1, 0);
        BigDecimal fourFour = oddsService.computeCorrectScoreOdds(m, 4, 4);

        assertThat(toDouble(oneNil)).isLessThan(toDouble(fourFour));
    }

    @Test
    void correctScoreOddsFavorFavoriteScorelinesOverReversedUnderdogScorelines() {
        Team strong = team(1L, "Strong FC", 95, 8, 0);
        Team weak = team(2L, "Weak FC", 45, 2, 0);
        Match m = match(strong, weak);

        BigDecimal favoriteWinsTwoNil = oddsService.computeCorrectScoreOdds(m, 2, 0);
        BigDecimal underdogWinsTwoNil = oddsService.computeCorrectScoreOdds(m, 0, 2);

        assertThat(toDouble(favoriteWinsTwoNil)).isLessThan(toDouble(underdogWinsTwoNil));
    }

    // ─── Handicap odds (Milestone 25) ─────────────────────────────────────

    @Test
    void getHandicapOddsOptions_returnsExactlyThreeSelectionsInOrder() {
        Team a = team(1L, "Team A", 80, 5, 0);
        Team b = team(2L, "Team B", 75, 5, 0);
        Match m = match(a, b);

        List<HandicapOddsOption> options = oddsService.getHandicapOddsOptions(m);

        assertThat(options).hasSize(3);
        assertThat(options.get(0).getSelection()).isEqualTo(HandicapSelection.HOME_MINUS_ONE);
        assertThat(options.get(1).getSelection()).isEqualTo(HandicapSelection.HANDICAP_DRAW);
        assertThat(options.get(2).getSelection()).isEqualTo(HandicapSelection.AWAY_PLUS_ONE);
    }

    @Test
    void handicapOdds_areWithinBoundsRoundedAndNeverNaNOrInfinite() {
        Team a = team(1L, "Team A", 80, 5, 0);
        Team b = team(2L, "Team B", 75, 5, 0);
        Match m = match(a, b);

        List<HandicapOddsOption> options = oddsService.getHandicapOddsOptions(m);

        for (HandicapOddsOption opt : options) {
            assertThat(opt.getOdds().scale()).isEqualTo(2);
            double value = toDouble(opt.getOdds());
            assertThat(Double.isNaN(value)).isFalse();
            assertThat(Double.isInfinite(value)).isFalse();
            assertThat(value).isGreaterThanOrEqualTo(1.20);
            assertThat(value).isLessThanOrEqualTo(25.00);
        }
    }

    @Test
    void handicapOdds_bookmakerMarginIsApplied_impliedProbSumGreaterThanOne() {
        Team a = team(1L, "Team A", 80, 5, 0);
        Team b = team(2L, "Team B", 75, 5, 0);
        Match m = match(a, b);

        List<HandicapOddsOption> options = oddsService.getHandicapOddsOptions(m);

        double impliedProbSum = options.stream()
                .mapToDouble(opt -> 1.0 / toDouble(opt.getOdds()))
                .sum();
        assertThat(impliedProbSum).isGreaterThan(1.0);
    }

    @Test
    void handicapOdds_strongHomeFavorite_hasLowerHomeMinus1OddsThanAwayFavorite() {
        // Strong home team: HOME_MINUS_ONE should be cheap (more likely)
        Team strongHome = team(1L, "Hapoel Beer Sheva", 92, 5, 0);
        Team weakAway   = team(2L, "Maccabi Bnei Reineh", 63, 5, 0);
        Match homeFavorite = match(strongHome, weakAway);

        // Strong away team: HOME_MINUS_ONE should be expensive (unlikely)
        Team weakHome   = team(3L, "Ironi Tiberias", 68, 5, 0);
        Team strongAway = team(4L, "Maccabi Tel Aviv", 91, 5, 0);
        Match awayFavorite = match(weakHome, strongAway);

        BigDecimal homeMinus1WhenHomeFavorite = oddsService.computeHandicapOdds(homeFavorite, HandicapSelection.HOME_MINUS_ONE);
        BigDecimal homeMinus1WhenAwayFavorite = oddsService.computeHandicapOdds(awayFavorite, HandicapSelection.HOME_MINUS_ONE);

        assertThat(toDouble(homeMinus1WhenHomeFavorite)).isLessThan(toDouble(homeMinus1WhenAwayFavorite));
    }

    @Test
    void handicapOdds_strongAwayFavorite_hasLowerAwayPlus1OddsThanHomeFavorite() {
        Team strongHome = team(1L, "Hapoel Beer Sheva", 92, 5, 0);
        Team weakAway   = team(2L, "Maccabi Bnei Reineh", 63, 5, 0);
        Match homeFavorite = match(strongHome, weakAway);

        Team weakHome   = team(3L, "Ironi Tiberias", 68, 5, 0);
        Team strongAway = team(4L, "Maccabi Tel Aviv", 91, 5, 0);
        Match awayFavorite = match(weakHome, strongAway);

        BigDecimal awayPlus1WhenHomeFavorite = oddsService.computeHandicapOdds(homeFavorite, HandicapSelection.AWAY_PLUS_ONE);
        BigDecimal awayPlus1WhenAwayFavorite = oddsService.computeHandicapOdds(awayFavorite, HandicapSelection.AWAY_PLUS_ONE);

        assertThat(toDouble(awayPlus1WhenAwayFavorite)).isLessThan(toDouble(awayPlus1WhenHomeFavorite));
    }

    @Test
    void handicapOdds_closeTeams_allThreeOutcomesHaveBalancedOdds() {
        Team netanya = team(1L, "Maccabi Netanya", 78, 5, 0);
        Team sakhnin = team(2L, "Bnei Sakhnin", 76, 5, 0);
        Match m = match(netanya, sakhnin);

        List<HandicapOddsOption> options = oddsService.getHandicapOddsOptions(m);

        // No single outcome should be an extreme outlier — all within a broad but reasonable range
        for (HandicapOddsOption opt : options) {
            assertThat(toDouble(opt.getOdds())).isBetween(1.20, 10.00);
        }
    }

    @Test
    void computeHandicapOdds_matchesGetHandicapOddsOptionsForSameSelection() {
        Team a = team(1L, "Team A", 80, 5, 0);
        Team b = team(2L, "Team B", 72, 5, 0);
        Match m = match(a, b);

        List<HandicapOddsOption> options = oddsService.getHandicapOddsOptions(m);

        for (HandicapOddsOption opt : options) {
            BigDecimal individual = oddsService.computeHandicapOdds(m, opt.getSelection());
            assertThat(individual).isEqualByComparingTo(opt.getOdds());
        }
    }

    // ─── Handicap odds coherence with the regular 1X2 market (Milestone 37 fix) ────
    // HOME_MINUS_ONE (win by 2+) is a strict subset of HOME_WIN, and AWAY_PLUS_ONE
    // (draw or away win) is exactly the complement of HOME_WIN — so these relationships
    // must hold for ANY match, not just hand-picked examples.

    @Test
    void handicapOdds_homeMinus1OddsAreGreaterThanRegularHomeWinOdds_sameMatch() {
        Team home = team(1L, "Maccabi Tel Aviv", 91, 5, 0);
        Team away = team(2L, "Ironi Tiberias", 68, 5, 0);
        Match m = match(home, away);

        BigDecimal[] oneXTwoOdds = oddsService.computeOdds(home, away, WeatherCondition.CLEAR, 0);
        BigDecimal homeMinus1Odds = oddsService.computeHandicapOdds(m, HandicapSelection.HOME_MINUS_ONE);

        // Winning by 2+ is strictly harder than winning at all, so it must be priced
        // as less likely (higher odds) than the plain home win — never lower.
        assertThat(toDouble(homeMinus1Odds)).isGreaterThan(toDouble(oneXTwoOdds[0]));
    }

    @Test
    void handicapOdds_homeMinus1OddsGreaterThanHomeWinOdds_acrossVariousMatchups() {
        record Pair(Team home, Team away) {}
        List<Pair> matchups = List.of(
            new Pair(team(1L, "Strong Home", 92, 8, 0), team(2L, "Weak Away", 60, 2, 0)),
            new Pair(team(3L, "Even Home", 78, 5, 0), team(4L, "Even Away", 76, 5, 0)),
            new Pair(team(5L, "Weak Home", 62, 3, 0), team(6L, "Strong Away", 90, 7, 0))
        );

        for (Pair p : matchups) {
            Match m = match(p.home(), p.away());
            BigDecimal[] oneXTwoOdds = oddsService.computeOdds(p.home(), p.away(), WeatherCondition.CLEAR, 0);
            BigDecimal homeMinus1Odds = oddsService.computeHandicapOdds(m, HandicapSelection.HOME_MINUS_ONE);
            assertThat(toDouble(homeMinus1Odds))
                .as("Home -1 odds must exceed regular home-win odds for %s vs %s", p.home().getName(), p.away().getName())
                .isGreaterThan(toDouble(oneXTwoOdds[0]));
        }
    }

    @Test
    void handicapOdds_homeMinus1ProbabilityIsLessThanHomeWinProbability() {
        Team home = team(1L, "Maccabi Tel Aviv", 91, 5, 0);
        Team away = team(2L, "Ironi Tiberias", 68, 5, 0);
        Match m = match(home, away);

        double[] oneXTwoProbs = oddsService.computeProbabilities(home, away, WeatherCondition.CLEAR, 0);
        double[] handicapProbs = oddsService.handicapBucketProbabilities(m);

        assertThat(handicapProbs[0]).as("P(HOME_MINUS_ONE)").isLessThan(oneXTwoProbs[0]);
    }

    @Test
    void handicapOdds_awayPlus1ProbabilityIsGreaterThanAwayWinProbability() {
        Team home = team(1L, "Maccabi Tel Aviv", 91, 5, 0);
        Team away = team(2L, "Ironi Tiberias", 68, 5, 0);
        Match m = match(home, away);

        double[] oneXTwoProbs = oddsService.computeProbabilities(home, away, WeatherCondition.CLEAR, 0);
        double[] handicapProbs = oddsService.handicapBucketProbabilities(m);

        // AWAY_PLUS_ONE = draw + away win, so it must exceed the plain away-win probability
        // whenever there is any draw probability at all (always true — draw is clamped to >= 0.16).
        assertThat(handicapProbs[2]).as("P(AWAY_PLUS_ONE)").isGreaterThan(oneXTwoProbs[2]);
        assertThat(handicapProbs[2]).isCloseTo(oneXTwoProbs[1] + oneXTwoProbs[2], org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void handicapOdds_homeMinus1PlusHandicapDrawEqualsHomeWinProbability() {
        Team home = team(1L, "Team A", 82, 6, 0);
        Team away = team(2L, "Team B", 74, 4, 0);
        Match m = match(home, away);

        double[] oneXTwoProbs = oddsService.computeProbabilities(home, away, WeatherCondition.CLEAR, 0);
        double[] handicapProbs = oddsService.handicapBucketProbabilities(m);

        // HOME_MINUS_ONE and HANDICAP_DRAW partition HOME_WIN exactly (margin (h-1) vs a
        // is either > 0, == 0, or the whole thing is not a home win at all).
        assertThat(handicapProbs[0] + handicapProbs[1])
            .isCloseTo(oneXTwoProbs[0], org.assertj.core.data.Offset.offset(0.0001));
    }

    // ─── Champion odds (Milestone 26) ─────────────────────────────────────────

    @Test
    void getChampionOdds_returnsAllTeamsSortedByOddsAscending() {
        List<Team> teams = List.of(
                team(1L, "Strong FC", 92, 8, 0),
                team(2L, "Average FC", 75, 5, 0),
                team(3L, "Weak FC", 63, 3, 0)
        );
        when(teamRepository.findAll()).thenReturn(teams);

        List<ChampionOddsOption> options = oddsService.getChampionOdds();

        assertThat(options).hasSize(3);
        // sorted ascending — favorites (lowest odds) first
        for (int i = 0; i < options.size() - 1; i++) {
            assertThat(options.get(i).getOdds().compareTo(options.get(i + 1).getOdds())).isLessThanOrEqualTo(0);
        }
    }

    @Test
    void getChampionOdds_oddsAreWithinBoundsAndRounded() {
        List<Team> teams = List.of(
                team(1L, "Strong", 95, 10, 0),
                team(2L, "Medium", 75, 5, 0),
                team(3L, "Weak", 63, 2, 0)
        );
        when(teamRepository.findAll()).thenReturn(teams);

        List<ChampionOddsOption> options = oddsService.getChampionOdds();

        for (ChampionOddsOption opt : options) {
            assertThat(opt.getOdds().scale()).isEqualTo(2);
            double val = toDouble(opt.getOdds());
            assertThat(Double.isNaN(val)).isFalse();
            assertThat(Double.isInfinite(val)).isFalse();
            assertThat(val).isGreaterThanOrEqualTo(1.40);
            assertThat(val).isLessThanOrEqualTo(80.00);
        }
    }

    @Test
    void getChampionOdds_strongerTeamHasLowerOddsThanWeakerTeam() {
        List<Team> teams = List.of(
                team(1L, "Strong", 92, 8, 0),
                team(2L, "Weak", 63, 3, 0)
        );
        when(teamRepository.findAll()).thenReturn(teams);

        List<ChampionOddsOption> options = oddsService.getChampionOdds();

        ChampionOddsOption strong = options.stream().filter(o -> o.getTeamId() == 1L).findFirst().orElseThrow();
        ChampionOddsOption weak   = options.stream().filter(o -> o.getTeamId() == 2L).findFirst().orElseThrow();
        assertThat(strong.getOdds()).isLessThan(weak.getOdds());
    }

    @Test
    void getChampionOdds_bookmakerMarginApplied_impliedProbSumGreaterThanOne() {
        List<Team> teams = List.of(
                team(1L, "A", 88, 7, 0),
                team(2L, "B", 78, 5, 0),
                team(3L, "C", 68, 4, 0)
        );
        when(teamRepository.findAll()).thenReturn(teams);

        List<ChampionOddsOption> options = oddsService.getChampionOdds();

        double impliedProbSum = options.stream()
                .mapToDouble(o -> 1.0 / toDouble(o.getOdds()))
                .sum();
        assertThat(impliedProbSum).isGreaterThan(1.0);
    }

    // ─── Top Scorer odds (Milestone 26) ────────────────────────────────────────

    private Player attackingPlayer(long id, String name, Position pos, int rating, boolean starter, Team team) {
        Player p = new Player();
        p.setId(id);
        p.setFullName(name);
        p.setPosition(pos);
        p.setRating(rating);
        p.setStarter(starter);
        p.setSubstitute(!starter);
        p.setTeam(team);
        return p;
    }

    @Test
    void getTopScorerOdds_excludesGkAndDefenders() {
        Team t = team(1L, "Team", 80, 5, 0);
        Player gk = attackingPlayer(1L, "Keeper", Position.GK, 75, true, t);
        Player cb = attackingPlayer(2L, "Defender", Position.CB, 75, true, t);
        Player st = attackingPlayer(3L, "Striker", Position.ST, 80, true, t);

        when(teamRepository.findAll()).thenReturn(List.of(t));
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(1L)).thenReturn(List.of(gk, cb, st));

        List<TopScorerOddsOption> options = oddsService.getTopScorerOdds(50);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).getPlayerId()).isEqualTo(3L);
    }

    @Test
    void getTopScorerOdds_oddsAreWithinBoundsAndRounded() {
        Team t = team(1L, "Team", 80, 5, 0);
        Player st1 = attackingPlayer(1L, "Star Striker", Position.ST, 90, true, t);
        Player am  = attackingPlayer(2L, "Attacking Mid", Position.AM, 70, true, t);
        Player cm  = attackingPlayer(3L, "Central Mid", Position.CM, 65, false, t);

        when(teamRepository.findAll()).thenReturn(List.of(t));
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(1L)).thenReturn(List.of(st1, am, cm));

        List<TopScorerOddsOption> options = oddsService.getTopScorerOdds(50);

        assertThat(options).isNotEmpty();
        for (TopScorerOddsOption opt : options) {
            assertThat(opt.getOdds().scale()).isEqualTo(2);
            double val = toDouble(opt.getOdds());
            assertThat(Double.isNaN(val)).isFalse();
            assertThat(Double.isInfinite(val)).isFalse();
            assertThat(val).isGreaterThanOrEqualTo(5.00);
            assertThat(val).isLessThanOrEqualTo(150.00);
        }
    }

    @Test
    void getTopScorerOdds_starterStrikerFromStrongTeamHasLowerOddsThanWeakSubBenchPlayer() {
        Team strongTeam = team(1L, "Strong", 92, 8, 0);
        Team weakTeam   = team(2L, "Weak", 63, 3, 0);
        Player starStriker = attackingPlayer(1L, "Star ST", Position.ST, 88, true, strongTeam);
        Player weakSub     = attackingPlayer(2L, "Weak AM", Position.AM, 60, false, weakTeam);

        when(teamRepository.findAll()).thenReturn(List.of(strongTeam, weakTeam));
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(1L)).thenReturn(List.of(starStriker));
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(2L)).thenReturn(List.of(weakSub));

        List<TopScorerOddsOption> options = oddsService.getTopScorerOdds(50);

        TopScorerOddsOption star = options.stream().filter(o -> o.getPlayerId() == 1L).findFirst().orElseThrow();
        TopScorerOddsOption weak = options.stream().filter(o -> o.getPlayerId() == 2L).findFirst().orElseThrow();
        assertThat(star.getOdds()).isLessThan(weak.getOdds());
    }

    @Test
    void getTopScorerOdds_bookmakerMarginApplied_impliedProbSumGreaterThanOne() {
        // 5 teams × 4 eligible players each = 20 candidates.
        // With T=6 this keeps each player's raw probability below the min-odds floor (no clamping),
        // so the bookmaker overround (implied-prob sum > 1.0) is preserved.
        long pid = 1;
        List<Team> allTeams = new ArrayList<>();
        int[] skills = {78, 76, 74, 72, 70};
        for (int i = 0; i < 5; i++) {
            long tid = i + 1;
            Team t = team(tid, "Team" + tid, skills[i], 5, 0);
            allTeams.add(t);
            int base = 74 - i;
            List<Player> squad = List.of(
                    attackingPlayer(pid++, "ST" + tid, Position.ST, base,     true, t),
                    attackingPlayer(pid++, "LW" + tid, Position.LW, base - 2, true, t),
                    attackingPlayer(pid++, "RW" + tid, Position.RW, base - 3, true, t),
                    attackingPlayer(pid++, "AM" + tid, Position.AM, base - 4, true, t)
            );
            when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(tid)).thenReturn(squad);
        }
        when(teamRepository.findAll()).thenReturn(allTeams);

        List<TopScorerOddsOption> options = oddsService.getTopScorerOdds(50);

        double impliedProbSum = options.stream()
                .mapToDouble(o -> 1.0 / toDouble(o.getOdds()))
                .sum();
        assertThat(impliedProbSum).isGreaterThan(1.0);
    }

    @Test
    void topScorer_topCandidateOddsInRealisticRange_6to18() {
        // Simulates a full 14-team league pool (1 elite + 13 average filler teams, 70 players total).
        // With a realistic pool size the elite team's starting ST should land in 6.00–18.00 odds.
        Team elite = team(1L, "Elite", 90, 8, 0);
        List<Player> eliteSquad = List.of(
                attackingPlayer(1L, "ST-e", Position.ST, 83, true,  elite),
                attackingPlayer(2L, "LW-e", Position.LW, 79, true,  elite),
                attackingPlayer(3L, "RW-e", Position.RW, 78, true,  elite),
                attackingPlayer(4L, "AM-e", Position.AM, 75, true,  elite),
                attackingPlayer(5L, "CM-e", Position.CM, 73, true,  elite)
        );
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(1L)).thenReturn(eliteSquad);

        List<Team> allTeams = new ArrayList<>();
        allTeams.add(elite);

        Position[] fivePositions = {Position.ST, Position.LW, Position.RW, Position.AM, Position.CM};
        int nextId = 6;
        for (long tid = 2; tid <= 14; tid++) {
            Team filler = team(tid, "Filler" + tid, 74, 5, 0);
            allTeams.add(filler);
            List<Player> fillerSquad = new ArrayList<>();
            for (Position pos : fivePositions) {
                fillerSquad.add(attackingPlayer(nextId, "fp" + nextId, pos, 70, true, filler));
                nextId++;
            }
            when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(tid)).thenReturn(fillerSquad);
        }
        when(teamRepository.findAll()).thenReturn(allTeams);

        List<TopScorerOddsOption> options = oddsService.getTopScorerOdds(50);

        assertThat(options).isNotEmpty();
        double topOdds = toDouble(options.get(0).getOdds());
        assertThat(topOdds).as("top favorite odds should be in 6.00–18.00 range").isBetween(6.00, 18.00);
    }

    @Test
    void topScorer_severalCandidatesBelow30_inDiversePool() {
        Team t1 = team(1L, "Team1", 88, 7, 0);
        Team t2 = team(2L, "Team2", 82, 6, 0);
        Team t3 = team(3L, "Team3", 76, 5, 0);

        List<Player> squad1 = List.of(
                attackingPlayer(1L, "ST1",  Position.ST, 82, true,  t1),
                attackingPlayer(2L, "LW1",  Position.LW, 79, true,  t1),
                attackingPlayer(3L, "RW1",  Position.RW, 78, true,  t1),
                attackingPlayer(4L, "AM1",  Position.AM, 74, true,  t1),
                attackingPlayer(5L, "CM1",  Position.CM, 71, true,  t1),
                attackingPlayer(6L, "STb1", Position.ST, 69, false, t1)
        );
        List<Player> squad2 = List.of(
                attackingPlayer(7L,  "ST2",  Position.ST, 78, true,  t2),
                attackingPlayer(8L,  "LW2",  Position.LW, 75, true,  t2),
                attackingPlayer(9L,  "RW2",  Position.RW, 74, true,  t2),
                attackingPlayer(10L, "AM2",  Position.AM, 72, true,  t2),
                attackingPlayer(11L, "CM2",  Position.CM, 69, true,  t2),
                attackingPlayer(12L, "STb2", Position.ST, 67, false, t2)
        );
        List<Player> squad3 = List.of(
                attackingPlayer(13L, "ST3",  Position.ST, 73, true,  t3),
                attackingPlayer(14L, "LW3",  Position.LW, 71, true,  t3),
                attackingPlayer(15L, "AM3",  Position.AM, 68, true,  t3),
                attackingPlayer(16L, "CM3",  Position.CM, 65, true,  t3),
                attackingPlayer(17L, "CMb3", Position.CM, 62, false, t3)
        );

        when(teamRepository.findAll()).thenReturn(List.of(t1, t2, t3));
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(1L)).thenReturn(squad1);
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(2L)).thenReturn(squad2);
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(3L)).thenReturn(squad3);

        List<TopScorerOddsOption> options = oddsService.getTopScorerOdds(50);

        long candidatesBelow30 = options.stream().filter(o -> toDouble(o.getOdds()) <= 30.00).count();
        assertThat(candidatesBelow30).as("at least 3 candidates should be below 30.00 odds").isGreaterThanOrEqualTo(3);
    }

    @Test
    void topScorer_oddsNotFlat_topRankMeaningfullyLowerThanLowRank() {
        // Build a 5-team pool (~25 candidates). Rank-1 odds should be substantially lower than rank-20 odds.
        Team t1 = team(1L, "A", 90, 7, 0);
        Team t2 = team(2L, "B", 80, 6, 0);
        Team t3 = team(3L, "C", 74, 5, 0);
        Team t4 = team(4L, "D", 68, 4, 0);
        Team t5 = team(5L, "E", 63, 3, 0);

        List<Player> s1 = List.of(
                attackingPlayer(1L,  "s1-1", Position.ST, 82, true,  t1),
                attackingPlayer(2L,  "s1-2", Position.LW, 78, true,  t1),
                attackingPlayer(3L,  "s1-3", Position.RW, 77, true,  t1),
                attackingPlayer(4L,  "s1-4", Position.AM, 74, true,  t1),
                attackingPlayer(5L,  "s1-5", Position.CM, 71, true,  t1)
        );
        List<Player> s2 = List.of(
                attackingPlayer(6L,  "s2-1", Position.ST, 75, true,  t2),
                attackingPlayer(7L,  "s2-2", Position.LW, 72, true,  t2),
                attackingPlayer(8L,  "s2-3", Position.RW, 71, true,  t2),
                attackingPlayer(9L,  "s2-4", Position.AM, 69, true,  t2),
                attackingPlayer(10L, "s2-5", Position.CM, 67, true,  t2)
        );
        List<Player> s3 = List.of(
                attackingPlayer(11L, "s3-1", Position.ST, 70, true,  t3),
                attackingPlayer(12L, "s3-2", Position.LW, 68, true,  t3),
                attackingPlayer(13L, "s3-3", Position.AM, 66, true,  t3),
                attackingPlayer(14L, "s3-4", Position.CM, 64, true,  t3),
                attackingPlayer(15L, "s3-5", Position.CM, 62, false, t3)
        );
        List<Player> s4 = List.of(
                attackingPlayer(16L, "s4-1", Position.ST, 68, true,  t4),
                attackingPlayer(17L, "s4-2", Position.LW, 66, true,  t4),
                attackingPlayer(18L, "s4-3", Position.AM, 64, true,  t4),
                attackingPlayer(19L, "s4-4", Position.CM, 62, true,  t4),
                attackingPlayer(20L, "s4-5", Position.CM, 60, false, t4)
        );
        List<Player> s5 = List.of(
                attackingPlayer(21L, "s5-1", Position.ST, 65, true,  t5),
                attackingPlayer(22L, "s5-2", Position.LW, 63, true,  t5),
                attackingPlayer(23L, "s5-3", Position.AM, 61, true,  t5),
                attackingPlayer(24L, "s5-4", Position.CM, 59, true,  t5),
                attackingPlayer(25L, "s5-5", Position.CM, 57, false, t5)
        );

        when(teamRepository.findAll()).thenReturn(List.of(t1, t2, t3, t4, t5));
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(1L)).thenReturn(s1);
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(2L)).thenReturn(s2);
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(3L)).thenReturn(s3);
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(4L)).thenReturn(s4);
        when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(5L)).thenReturn(s5);

        List<TopScorerOddsOption> options = oddsService.getTopScorerOdds(50);

        assertThat(options).hasSizeGreaterThanOrEqualTo(20);
        double rank1Odds  = toDouble(options.get(0).getOdds());
        double rank20Odds = toDouble(options.get(19).getOdds());
        assertThat(rank1Odds).as("rank-1 odds should be at least 3× lower than rank-20 odds")
                .isLessThan(rank20Odds / 3.0);
    }

    @Test
    void topScorer_positionOrderingRespected_sameTeamRatingAndStarterStatus() {
        // With identical team, rating, and starter status, position drives odds ordering:
        // ST (lowest odds / most likely scorer) < LW/RW < AM < CM (highest odds).
        // Uses a full 14-team pool (70 players) so probabilities don't collapse to the min floor.
        List<Team> allTeams = new ArrayList<>();
        Position[] positions = {Position.ST, Position.LW, Position.RW, Position.AM, Position.CM};
        int nextId = 1;
        for (long tid = 1; tid <= 14; tid++) {
            Team t = team(tid, "Team" + tid, 80, 5, 0);
            allTeams.add(t);
            List<Player> squad = new ArrayList<>();
            for (Position pos : positions) {
                squad.add(attackingPlayer(nextId++, pos.name() + "-t" + tid, pos, 75, true, t));
            }
            when(playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(tid)).thenReturn(squad);
        }
        when(teamRepository.findAll()).thenReturn(allTeams);

        List<TopScorerOddsOption> options = oddsService.getTopScorerOdds(70);

        // All STs have equal odds, all LWs equal, etc. — just compare any representative from each position.
        TopScorerOddsOption stOpt = options.stream().filter(o -> o.getPosition() == Position.ST).findFirst().orElseThrow();
        TopScorerOddsOption lwOpt = options.stream().filter(o -> o.getPosition() == Position.LW).findFirst().orElseThrow();
        TopScorerOddsOption rwOpt = options.stream().filter(o -> o.getPosition() == Position.RW).findFirst().orElseThrow();
        TopScorerOddsOption amOpt = options.stream().filter(o -> o.getPosition() == Position.AM).findFirst().orElseThrow();
        TopScorerOddsOption cmOpt = options.stream().filter(o -> o.getPosition() == Position.CM).findFirst().orElseThrow();

        assertThat(stOpt.getOdds()).isLessThan(lwOpt.getOdds());
        assertThat(stOpt.getOdds()).isLessThan(rwOpt.getOdds());
        assertThat(lwOpt.getOdds()).isLessThan(amOpt.getOdds());
        assertThat(rwOpt.getOdds()).isLessThan(amOpt.getOdds());
        assertThat(amOpt.getOdds()).isLessThan(cmOpt.getOdds());
    }
}

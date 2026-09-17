package com.footballsim.service;

import com.footballsim.dto.ChampionOddsOption;
import com.footballsim.dto.CorrectScoreOddsOption;
import com.footballsim.dto.HandicapOddsOption;
import com.footballsim.dto.TopScorerOddsOption;
import com.footballsim.entity.Match;
import com.footballsim.entity.Player;
import com.footballsim.entity.Team;
import com.footballsim.enums.HandicapSelection;
import com.footballsim.enums.PlayerStatus;
import com.footballsim.enums.Position;
import com.footballsim.enums.WeatherCondition;
import com.footballsim.repository.PlayerRepository;
import com.footballsim.repository.TeamRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Computes realistic 1X2 odds from a probability model driven by team strength
 * (skill, morale, home advantage, availability, lineup quality, weather).
 * Internal model only — not real bookmaker odds (a bookmaker margin is added on top).
 */
@Service
public class OddsService {

    private static final double HOME_ADVANTAGE = 4.0;
    private static final double MORALE_FACTOR = 1.5;

    /** Power penalty for an unavailable (injured/suspended) starter vs. a substitute, capped so a team's power is never destroyed. */
    private static final double UNAVAILABLE_STARTER_PENALTY = 2.0;
    private static final double UNAVAILABLE_SUBSTITUTE_PENALTY = 0.8;
    private static final double MAX_UNAVAILABLE_PENALTY = 8.0;
    /** Fallback penalty when no squad data exists for a team — based on the legacy team.injuries counter. */
    private static final double FALLBACK_INJURY_PENALTY_PER_PLAYER = 1.2;
    private static final double MAX_FALLBACK_INJURY_PENALTY = 6.0;

    /** Lineup-strength adjustment: how far the available starters' average rating sits from a typical squad rating. */
    private static final double TYPICAL_STARTER_RATING = 70.0;
    private static final double LINEUP_STRENGTH_FACTOR = 0.2;
    private static final double MAX_LINEUP_STRENGTH_ADJUSTMENT = 3.0;

    /** Draw probability shrinks as teams become more uneven, grows as they become evenly matched. */
    private static final double BASE_DRAW_PROBABILITY = 0.28;
    private static final double DRAW_DIFF_SENSITIVITY = 0.002;
    private static final double MIN_DRAW_PROBABILITY = 0.16;
    private static final double MAX_DRAW_PROBABILITY = 0.30;
    private static final double WET_WEATHER_DRAW_BONUS = 0.02;

    /** Power-difference-to-win-share scaling: larger values flatten the curve, smaller values sharpen it. */
    private static final double POWER_DIFF_SCALE = 16.0;

    private static final double MAX_FAVORITE_PROBABILITY = 0.76;
    private static final double MIN_UNDERDOG_PROBABILITY = 0.04;

    private static final double BOOKMAKER_MARGIN = 1.07;
    private static final BigDecimal MIN_ODDS = BigDecimal.valueOf(1.20);
    private static final BigDecimal MAX_ODDS = BigDecimal.valueOf(15.00);

    /** Correct-score model: expected goals per team, derived from the same team-power difference used for 1X2. */
    private static final double BASE_EXPECTED_GOALS = 1.35;
    private static final double EXPECTED_GOALS_POWER_SCALE = 28.0;
    private static final double MIN_EXPECTED_GOALS = 0.45;
    private static final double MAX_EXPECTED_GOALS = 3.2;

    /** Correct-score odds carry a larger margin than 1X2 since there are many more possible outcomes. */
    private static final double CORRECT_SCORE_MARGIN = 1.15;
    private static final BigDecimal MIN_CORRECT_SCORE_ODDS = new BigDecimal("4.00");
    private static final BigDecimal MAX_CORRECT_SCORE_ODDS = new BigDecimal("80.00");

    /** Handicap odds (3-way European Handicap -1) use a margin between 1X2 and correct-score. */
    private static final double HANDICAP_MARGIN = 1.10;
    private static final BigDecimal MIN_HANDICAP_ODDS = new BigDecimal("1.20");
    private static final BigDecimal MAX_HANDICAP_ODDS = new BigDecimal("25.00");
    /** Score grid upper bound for handicap probability bucketing (0..HANDICAP_GRID_MAX inclusive). */
    private static final int HANDICAP_GRID_MAX = 6;

    /** GET /api/matches/{matchId}/correct-score-odds returns all combinations of 0..MAX_POPULAR_GOALS for each side. */
    private static final int MAX_POPULAR_GOALS = 4;

    /**
     * Champion odds: exp(skillLevel / CHAMPION_SKILL_SCALE) gives a realistic spread across 14 teams.
     * Morale contributes a small bonus. Average available-starter rating adds a tiny adjustment.
     */
    private static final double CHAMPION_SKILL_SCALE = 18.0;
    private static final double CHAMPION_MORALE_WEIGHT = 0.03;
    private static final double CHAMPION_RATING_WEIGHT = 0.002;
    private static final double CHAMPION_TYPICAL_RATING = 65.0;
    private static final double CHAMPION_MARGIN = 1.12;
    private static final BigDecimal MIN_CHAMPION_ODDS = new BigDecimal("1.40");
    private static final BigDecimal MAX_CHAMPION_ODDS = new BigDecimal("80.00");

    /**
     * Top-scorer odds: only attacking positions are candidates.
     * Linear strength is exponentiated with a sharp temperature to amplify differences
     * between strong candidates (high-rated starting ST from a strong team) and weak ones.
     *
     * linearStrength = ratingContrib + positionWeight + starterBonus + teamAttack
     *   ratingContrib  = (rating - RATING_BASELINE) / RATING_SCALE
     *   teamAttack     = (skillLevel - TEAM_SKILL_BASELINE) / TEAM_SKILL_SCALE
     * strength = exp(linearStrength / TOP_SCORER_TEMPERATURE)
     */
    private static final Set<Position> TOP_SCORER_POSITIONS = Set.of(
            Position.ST, Position.LW, Position.RW, Position.AM, Position.CM);
    private static final double TOP_SCORER_TEMPERATURE = 6.0;
    private static final double TOP_SCORER_RATING_BASELINE = 65.0;
    private static final double TOP_SCORER_RATING_SCALE = 3.0;
    private static final double TOP_SCORER_TEAM_SKILL_BASELINE = 65.0;
    private static final double TOP_SCORER_TEAM_SKILL_SCALE = 5.0;
    private static final double TOP_SCORER_STARTER_BONUS = 8.0;
    private static final double TOP_SCORER_MARGIN = 1.20;
    private static final BigDecimal MIN_TOP_SCORER_ODDS = new BigDecimal("5.00");
    private static final BigDecimal MAX_TOP_SCORER_ODDS = new BigDecimal("150.00");
    private static final int DEFAULT_TOP_SCORER_LIMIT = 50;

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private Random random = new Random();

    public OddsService(PlayerRepository playerRepository, TeamRepository teamRepository) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
    }

    /** Test-only seam: lets unit tests inject a deterministic Random to avoid flaky assertions on weather jitter. */
    void setRandom(Random random) {
        this.random = random;
    }

    public BigDecimal[] computeOdds(Team home, Team away, WeatherCondition condition, Integer impact) {
        double[] p = probabilities(home, away, condition, impact);
        return new BigDecimal[]{toOdds(p[0]), toOdds(p[1]), toOdds(p[2])};
    }

    public double[] computeProbabilities(Team home, Team away, WeatherCondition condition, Integer impact) {
        return probabilities(home, away, condition, impact);
    }

    /** The full set of frozen match-market prices (1X2 + European Handicap -1) for one match. */
    public static final class MatchMarketOdds {
        public final BigDecimal home, draw, away;
        public final BigDecimal handicapHomeMinusOne, handicapDraw, handicapAwayPlusOne;

        MatchMarketOdds(BigDecimal home, BigDecimal draw, BigDecimal away,
                        BigDecimal handicapHomeMinusOne, BigDecimal handicapDraw, BigDecimal handicapAwayPlusOne) {
            this.home = home; this.draw = draw; this.away = away;
            this.handicapHomeMinusOne = handicapHomeMinusOne;
            this.handicapDraw = handicapDraw;
            this.handicapAwayPlusOne = handicapAwayPlusOne;
        }
    }

    /**
     * Prices every match market from ONE probability vector so the stored 1X2 and handicap
     * odds are mutually coherent (Home -1 always longer than Home Win) and — once stored on
     * the Match when betting opens — never change between display and bet placement.
     */
    public MatchMarketOdds priceMatchMarkets(Match match) {
        double[] oneXTwo = probabilities(match.getHomeTeam(), match.getAwayTeam(),
                match.getWeatherCondition(), match.getWeatherImpact());
        double[] handicap = handicapBucketProbabilities(match, oneXTwo);
        return new MatchMarketOdds(
                toOdds(oneXTwo[0]), toOdds(oneXTwo[1]), toOdds(oneXTwo[2]),
                toHandicapOdds(handicap[0]), toHandicapOdds(handicap[1]), toHandicapOdds(handicap[2]));
    }

    /** Odds for a single exact scoreline, derived from a Poisson model of expected goals. */
    public BigDecimal computeCorrectScoreOdds(Match match, int homeGoals, int awayGoals) {
        double[] expectedGoals = expectedGoals(match);
        double prob = poissonPmf(homeGoals, expectedGoals[0]) * poissonPmf(awayGoals, expectedGoals[1]);
        return toCorrectScoreOdds(prob);
    }

    /** All correct-score options (0..4 home goals x 0..4 away goals = 25), grouped by home goals then away goals. */
    public List<CorrectScoreOddsOption> getPopularCorrectScores(Match match) {
        double[] expectedGoals = expectedGoals(match);
        List<CorrectScoreOddsOption> options = new ArrayList<>();
        for (int home = 0; home <= MAX_POPULAR_GOALS; home++) {
            for (int away = 0; away <= MAX_POPULAR_GOALS; away++) {
                double prob = poissonPmf(home, expectedGoals[0]) * poissonPmf(away, expectedGoals[1]);
                options.add(new CorrectScoreOddsOption(home, away, toCorrectScoreOdds(prob)));
            }
        }
        return options;
    }

    /**
     * Odds for one European Handicap -1 (home) outcome, derived from a Poisson score grid.
     * Uses the same expected-goals model as correct-score odds; sums score probabilities into
     * the three handicap buckets and applies a bookmaker margin identical in convention to
     * {@link #toOdds}/{@link #toCorrectScoreOdds}: odds = 1 / (probability * margin).
     */
    public BigDecimal computeHandicapOdds(Match match, HandicapSelection selection) {
        double prob = handicapBucketProbabilities(match)[selection.ordinal()];
        return toHandicapOdds(prob);
    }

    /** The three selectable handicap outcomes with labels and current odds for the given match. */
    public List<HandicapOddsOption> getHandicapOddsOptions(Match match) {
        double[] probs = handicapBucketProbabilities(match);
        return List.of(
            new HandicapOddsOption(HandicapSelection.HOME_MINUS_ONE, "Home -1",     toHandicapOdds(probs[0])),
            new HandicapOddsOption(HandicapSelection.HANDICAP_DRAW,  "Handicap Draw", toHandicapOdds(probs[1])),
            new HandicapOddsOption(HandicapSelection.AWAY_PLUS_ONE,  "Away +1",     toHandicapOdds(probs[2]))
        );
    }

    /**
     * Derives the 3 handicap buckets so they are always coherent with the real, displayed 1X2
     * market — not from an independent probability model. This matters because two exact
     * event relationships hold for ANY scoring distribution:
     *   HOME_MINUS_ONE (home wins by 2+)      is a strict SUBSET of HOME_WIN
     *   AWAY_PLUS_ONE  (draw or away win)     is EXACTLY the complement of HOME_WIN
     * So instead of computing handicap probabilities from a second, independently-tuned Poisson
     * model (which previously let HOME_MINUS_ONE outprice HOME_WIN — impossible in a real market),
     * we anchor both buckets to the actual pHomeWin/pDraw/pAwayWin used for the 1X2 odds, and use
     * the existing Poisson goal grid only to find a realistic within-home-win split between
     * "won by exactly 1" and "won by 2+". This guarantees, by construction:
     *   P(HOME_MINUS_ONE) = pHomeWin * (1 - frac1) < pHomeWin           (as long as frac1 > 0)
     *   P(AWAY_PLUS_ONE)  = pDraw + pAwayWin        > pAwayWin          (as long as pDraw > 0, always true)
     */
    /** Package-private (not private) so unit tests can assert on raw probabilities directly, same pattern as {@link #calculateTeamPower}. */
    double[] handicapBucketProbabilities(Match match) {
        double[] oneXTwo = probabilities(match.getHomeTeam(), match.getAwayTeam(), match.getWeatherCondition(), match.getWeatherImpact());
        return handicapBucketProbabilities(match, oneXTwo);
    }

    /** Same derivation, but anchored to a caller-supplied 1X2 vector (used to freeze coherent markets). */
    double[] handicapBucketProbabilities(Match match, double[] oneXTwo) {
        double pHomeWin = oneXTwo[0];
        double pDraw = oneXTwo[1];
        double pAwayWin = oneXTwo[2];

        double frac1 = homeWinMarginExactlyOneFraction(match);

        double homeMinus1 = pHomeWin * (1.0 - frac1);
        double handicapDraw = pHomeWin * frac1;
        double awayPlus1 = pDraw + pAwayWin;
        return new double[]{homeMinus1, handicapDraw, awayPlus1};
    }

    /**
     * Uses the existing Poisson goal grid (same model as correct-score odds) purely to estimate
     * what fraction of "home win" scorelines have a margin of exactly 1 goal vs. 2+. This is a
     * within-home-win refinement ratio only — it never determines the absolute probability of
     * home winning, draws, or away winning (those come from the real 1X2 model above).
     */
    private double homeWinMarginExactlyOneFraction(Match match) {
        double[] lambda = expectedGoals(match);
        double marginExactlyOne = 0.0;
        double marginAtLeastTwo = 0.0;

        for (int h = 0; h <= HANDICAP_GRID_MAX; h++) {
            for (int a = 0; a <= HANDICAP_GRID_MAX; a++) {
                double p = poissonPmf(h, lambda[0]) * poissonPmf(a, lambda[1]);
                int diff = h - a;
                if (diff == 1)      marginExactlyOne += p;
                else if (diff >= 2) marginAtLeastTwo += p;
            }
        }

        double totalHomeWinMass = marginExactlyOne + marginAtLeastTwo;
        if (totalHomeWinMass <= 0.0) {
            return 0.5;
        }
        return marginExactlyOne / totalHomeWinMass;
    }

    private BigDecimal toHandicapOdds(double prob) {
        if (prob <= 0.0) {
            return MAX_HANDICAP_ODDS;
        }
        BigDecimal odds = BigDecimal.valueOf(1.0 / (prob * HANDICAP_MARGIN)).setScale(2, RoundingMode.HALF_UP);
        if (odds.compareTo(MIN_HANDICAP_ODDS) < 0) {
            return MIN_HANDICAP_ODDS;
        }
        if (odds.compareTo(MAX_HANDICAP_ODDS) > 0) {
            return MAX_HANDICAP_ODDS;
        }
        return odds;
    }

    /**
     * Expected goals for home/away, derived from the same team-power difference used for 1X2 probabilities.
     * A stronger team (higher power diff) gets a higher expected-goals share; an evenly matched
     * fixture centers both teams on {@link #BASE_EXPECTED_GOALS}.
     */
    private double[] expectedGoals(Match match) {
        List<Player> homePlayers = playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(match.getHomeTeam().getId());
        List<Player> awayPlayers = playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(match.getAwayTeam().getId());

        double homePower = calculateTeamPower(match.getHomeTeam(), homePlayers, true, match.getWeatherImpact());
        double awayPower = calculateTeamPower(match.getAwayTeam(), awayPlayers, false, match.getWeatherImpact());
        double diff = homePower - awayPower;

        double lambdaHome = clamp(BASE_EXPECTED_GOALS * Math.exp(diff / EXPECTED_GOALS_POWER_SCALE), MIN_EXPECTED_GOALS, MAX_EXPECTED_GOALS);
        double lambdaAway = clamp(BASE_EXPECTED_GOALS * Math.exp(-diff / EXPECTED_GOALS_POWER_SCALE), MIN_EXPECTED_GOALS, MAX_EXPECTED_GOALS);
        return new double[]{lambdaHome, lambdaAway};
    }

    private double poissonPmf(int goals, double expectedGoals) {
        return Math.exp(-expectedGoals) * Math.pow(expectedGoals, goals) / factorial(goals);
    }

    private double factorial(int n) {
        double result = 1.0;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    private BigDecimal toCorrectScoreOdds(double prob) {
        if (prob <= 0.0) {
            return MAX_CORRECT_SCORE_ODDS;
        }
        BigDecimal odds = BigDecimal.valueOf(1.0 / (prob * CORRECT_SCORE_MARGIN)).setScale(2, RoundingMode.HALF_UP);
        if (odds.compareTo(MIN_CORRECT_SCORE_ODDS) < 0) {
            return MIN_CORRECT_SCORE_ODDS;
        }
        if (odds.compareTo(MAX_CORRECT_SCORE_ODDS) > 0) {
            return MAX_CORRECT_SCORE_ODDS;
        }
        return odds;
    }

    private double[] probabilities(Team home, Team away, WeatherCondition condition, Integer impact) {
        WeatherCondition effectiveCondition = condition != null ? condition : WeatherCondition.CLEAR;

        List<Player> homePlayers = playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(home.getId());
        List<Player> awayPlayers = playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(away.getId());

        double homePower = calculateTeamPower(home, homePlayers, true, impact);
        double awayPower = calculateTeamPower(away, awayPlayers, false, impact);
        double diff = homePower - awayPower;

        double drawProb = BASE_DRAW_PROBABILITY - Math.abs(diff) * DRAW_DIFF_SENSITIVITY;
        if (effectiveCondition == WeatherCondition.RAIN || effectiveCondition == WeatherCondition.STORM) {
            drawProb += WET_WEATHER_DRAW_BONUS;
        }
        drawProb = clamp(drawProb, MIN_DRAW_PROBABILITY, MAX_DRAW_PROBABILITY);

        double nonDrawProb = 1.0 - drawProb;
        double homeShare = logistic(diff / POWER_DIFF_SCALE);
        double homeWinProb = nonDrawProb * homeShare;
        double awayWinProb = nonDrawProb * (1.0 - homeShare);

        double[] probs = applyWeatherJitter(new double[]{homeWinProb, drawProb, awayWinProb}, effectiveCondition);
        return clampAndNormalize(probs);
    }

    /**
     * teamPower = skillLevel + morale*1.5 + homeAdvantage(home only)
     *             - unavailablePenalty + lineupStrengthAdjustment + weatherImpact
     */
    double calculateTeamPower(Team team, List<Player> players, boolean home, Integer weatherImpact) {
        double power = team.getSkillLevel() + team.getMorale() * MORALE_FACTOR;
        if (home) {
            power += HOME_ADVANTAGE;
        }
        power -= unavailablePenalty(team, players);
        power += lineupStrengthAdjustment(players);
        power += weatherImpact != null ? weatherImpact : 0;
        return Math.max(power, 1.0);
    }

    /** Penalizes missing starters more than missing substitutes; falls back to the legacy injuries counter when no squad data exists. */
    private double unavailablePenalty(Team team, List<Player> players) {
        if (players.isEmpty()) {
            return Math.min(team.getInjuries() * FALLBACK_INJURY_PENALTY_PER_PLAYER, MAX_FALLBACK_INJURY_PENALTY);
        }

        long unavailableStarters = players.stream().filter(p -> p.isStarter() && isUnavailable(p)).count();
        long unavailableSubstitutes = players.stream().filter(p -> p.isSubstitute() && isUnavailable(p)).count();
        double penalty = unavailableStarters * UNAVAILABLE_STARTER_PENALTY + unavailableSubstitutes * UNAVAILABLE_SUBSTITUTE_PENALTY;
        return Math.min(penalty, MAX_UNAVAILABLE_PENALTY);
    }

    private boolean isUnavailable(Player player) {
        return player.getStatus() == PlayerStatus.SUSPENDED
                || (player.getStatus() == PlayerStatus.INJURED && player.getInjuryMatchesRemaining() > 0)
                || player.getSuspensionMatchesRemaining() > 0;
    }

    /** Small bounded bonus/penalty based on how the available starters' average rating compares to a typical squad. */
    private double lineupStrengthAdjustment(List<Player> players) {
        List<Player> availableStarters = players.stream()
                .filter(Player::isStarter)
                .filter(p -> !isUnavailable(p))
                .toList();
        if (availableStarters.isEmpty()) {
            return 0.0;
        }

        double avgRating = availableStarters.stream().mapToInt(Player::getRating).average().orElse(TYPICAL_STARTER_RATING);
        double adjustment = (avgRating - TYPICAL_STARTER_RATING) * LINEUP_STRENGTH_FACTOR;
        return clamp(adjustment, -MAX_LINEUP_STRENGTH_ADJUSTMENT, MAX_LINEUP_STRENGTH_ADJUSTMENT);
    }

    private double logistic(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /**
     * Adds small symmetric random noise to home/away probabilities (transferring the difference
     * to/from the draw probability) to represent extra unpredictability in windy/stormy/cold
     * conditions. Magnitude is small and bounded so skill remains the dominant factor.
     */
    private double[] applyWeatherJitter(double[] probs, WeatherCondition condition) {
        double magnitude = switch (condition) {
            case WIND -> 0.06;
            case STORM -> 0.05;
            case COLD -> 0.02;
            default -> 0.0;
        };
        if (magnitude == 0.0) {
            return probs;
        }

        double jitter = (random.nextDouble() * 2 * magnitude) - magnitude;
        double home = Math.max(probs[0] + jitter, 0.01);
        double away = Math.max(probs[2] - jitter, 0.01);
        double draw = Math.max(probs[1], 0.01);
        return new double[]{home, draw, away};
    }

    /** Clamps favorite/underdog/draw probabilities to realistic bounds, then renormalizes so they sum to 1.0. */
    private double[] clampAndNormalize(double[] probs) {
        double home = probs[0];
        double draw = clamp(probs[1], MIN_DRAW_PROBABILITY, MAX_DRAW_PROBABILITY);
        double away = probs[2];

        home = clampFavoriteUnderdog(home, away);
        away = clampFavoriteUnderdog(away, home);

        double total = home + draw + away;
        return new double[]{home / total, draw / total, away / total};
    }

    private double clampFavoriteUnderdog(double prob, double opposing) {
        double clamped = clamp(prob, MIN_UNDERDOG_PROBABILITY, MAX_FAVORITE_PROBABILITY);
        // Only treat as the "favorite" cap if this side actually leads; otherwise only the underdog floor applies.
        if (prob <= opposing) {
            return Math.max(prob, MIN_UNDERDOG_PROBABILITY);
        }
        return clamped;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private BigDecimal toOdds(double prob) {
        BigDecimal odds = BigDecimal.valueOf(1.0 / (prob * BOOKMAKER_MARGIN)).setScale(2, RoundingMode.HALF_UP);
        if (odds.compareTo(MIN_ODDS) < 0) {
            return MIN_ODDS;
        }
        if (odds.compareTo(MAX_ODDS) > 0) {
            return MAX_ODDS;
        }
        return odds;
    }

    // ─── Season odds ─────────────────────────────────────────────────────────

    /**
     * Returns champion odds for all 14 teams, sorted by odds ascending (favorites first).
     * Formula: strength = exp(skillLevel / CHAMPION_SKILL_SCALE)
     *                     * (1 + morale * CHAMPION_MORALE_WEIGHT)
     *                     * (1 + (avgAvailableStarterRating - 65) * CHAMPION_RATING_WEIGHT)
     * Normalize across all teams, apply CHAMPION_MARGIN, clamp to [1.40, 80.00].
     */
    public List<ChampionOddsOption> getChampionOdds() {
        List<Team> teams = teamRepository.findAll();
        if (teams.isEmpty()) {
            return List.of();
        }
        double[] strengths = computeChampionStrengths(teams);
        double total = 0.0;
        for (double s : strengths) {
            total += s;
        }
        List<ChampionOddsOption> options = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            double prob = strengths[i] / total;
            BigDecimal odds = toChampionOdds(prob);
            Team team = teams.get(i);
            String name = team.getDisplayName() != null ? team.getDisplayName() : team.getName();
            options.add(new ChampionOddsOption(team.getId(), name, team.getSkillLevel(), odds));
        }
        options.sort(Comparator.comparing(ChampionOddsOption::getOdds));
        return options;
    }

    /**
     * Odds snapshot for a specific team at placement time.
     * Re-computes the full distribution to ensure the snapshot is consistent.
     */
    public BigDecimal getChampionOddsForTeam(Team team) {
        List<Team> teams = teamRepository.findAll();
        double[] strengths = computeChampionStrengths(teams);
        double total = 0.0;
        for (double s : strengths) {
            total += s;
        }
        for (int i = 0; i < teams.size(); i++) {
            if (teams.get(i).getId().equals(team.getId())) {
                return toChampionOdds(strengths[i] / total);
            }
        }
        return MAX_CHAMPION_ODDS;
    }

    private double[] computeChampionStrengths(List<Team> teams) {
        double[] strengths = new double[teams.size()];
        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            List<Player> players = playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(team.getId());
            double avgStarterRating = players.stream()
                    .filter(Player::isStarter)
                    .filter(p -> !isUnavailable(p))
                    .mapToInt(Player::getRating)
                    .average()
                    .orElse(CHAMPION_TYPICAL_RATING);
            double strength = Math.exp(team.getSkillLevel() / CHAMPION_SKILL_SCALE)
                    * (1.0 + team.getMorale() * CHAMPION_MORALE_WEIGHT)
                    * (1.0 + (avgStarterRating - CHAMPION_TYPICAL_RATING) * CHAMPION_RATING_WEIGHT);
            strengths[i] = Math.max(strength, 0.001);
        }
        return strengths;
    }

    private BigDecimal toChampionOdds(double prob) {
        if (prob <= 0.0) {
            return MAX_CHAMPION_ODDS;
        }
        BigDecimal odds = BigDecimal.valueOf(1.0 / (prob * CHAMPION_MARGIN)).setScale(2, RoundingMode.HALF_UP);
        if (odds.compareTo(MIN_CHAMPION_ODDS) < 0) {
            return MIN_CHAMPION_ODDS;
        }
        if (odds.compareTo(MAX_CHAMPION_ODDS) > 0) {
            return MAX_CHAMPION_ODDS;
        }
        return odds;
    }

    /**
     * Returns top-scorer odds for eligible attacking candidates, sorted by odds ascending.
     * Eligible positions: ST, LW, RW, AM, CM. Unavailable (injured/suspended) players excluded.
     * Formula: linearStrength = rating + positionWeight + starterBonus + team.skillLevel * factor
     *          strength = exp(linearStrength / TOP_SCORER_SKILL_SCALE)
     * Normalize across candidates, apply TOP_SCORER_MARGIN, clamp to [3.00, 150.00].
     */
    public List<TopScorerOddsOption> getTopScorerOdds(int limit) {
        List<Team> teams = teamRepository.findAll();
        List<Player> candidates = new ArrayList<>();
        for (Team team : teams) {
            List<Player> players = playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(team.getId());
            for (Player p : players) {
                if (TOP_SCORER_POSITIONS.contains(p.getPosition()) && !isUnavailable(p)) {
                    candidates.add(p);
                }
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        double[] strengths = computeTopScorerStrengths(candidates);
        double total = 0.0;
        for (double s : strengths) {
            total += s;
        }
        List<TopScorerOddsOption> options = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Player p = candidates.get(i);
            double prob = strengths[i] / total;
            BigDecimal odds = toTopScorerOdds(prob);
            String teamName = p.getTeam().getDisplayName() != null ? p.getTeam().getDisplayName() : p.getTeam().getName();
            options.add(new TopScorerOddsOption(p.getId(), p.getFullName(), p.getTeam().getId(),
                    teamName, p.getPosition(), p.getRating(), odds));
        }
        options.sort(Comparator.comparing(TopScorerOddsOption::getOdds));
        if (limit > 0 && options.size() > limit) {
            return options.subList(0, limit);
        }
        return options;
    }

    /**
     * Odds snapshot for a specific player at placement time.
     * Re-computes the full distribution across all eligible candidates.
     */
    public BigDecimal getTopScorerOddsForPlayer(Player player) {
        List<Team> teams = teamRepository.findAll();
        List<Player> candidates = new ArrayList<>();
        for (Team team : teams) {
            List<Player> players = playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(team.getId());
            for (Player p : players) {
                if (TOP_SCORER_POSITIONS.contains(p.getPosition()) && !isUnavailable(p)) {
                    candidates.add(p);
                }
            }
        }
        // Include the betted player even if newly unavailable (snapshot at placement)
        if (candidates.stream().noneMatch(c -> c.getId().equals(player.getId()))) {
            candidates.add(player);
        }
        double[] strengths = computeTopScorerStrengths(candidates);
        double total = 0.0;
        for (double s : strengths) {
            total += s;
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).getId().equals(player.getId())) {
                return toTopScorerOdds(strengths[i] / total);
            }
        }
        return MAX_TOP_SCORER_ODDS;
    }

    private double[] computeTopScorerStrengths(List<Player> candidates) {
        double[] strengths = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            Player p = candidates.get(i);
            double posWeight = switch (p.getPosition()) {
                case ST -> 16.0;
                case LW, RW -> 10.0;
                case AM -> 5.0;
                case CM -> 1.0;
                default -> 0.0;
            };
            double ratingContrib = (p.getRating() - TOP_SCORER_RATING_BASELINE) / TOP_SCORER_RATING_SCALE;
            double starterBonus = p.isStarter() ? TOP_SCORER_STARTER_BONUS : 0.0;
            double teamAttack = (p.getTeam().getSkillLevel() - TOP_SCORER_TEAM_SKILL_BASELINE) / TOP_SCORER_TEAM_SKILL_SCALE;
            double linear = ratingContrib + posWeight + starterBonus + teamAttack;
            strengths[i] = Math.exp(linear / TOP_SCORER_TEMPERATURE);
        }
        return strengths;
    }

    private BigDecimal toTopScorerOdds(double prob) {
        if (prob <= 0.0) {
            return MAX_TOP_SCORER_ODDS;
        }
        BigDecimal odds = BigDecimal.valueOf(1.0 / (prob * TOP_SCORER_MARGIN)).setScale(2, RoundingMode.HALF_UP);
        if (odds.compareTo(MIN_TOP_SCORER_ODDS) < 0) {
            return MIN_TOP_SCORER_ODDS;
        }
        if (odds.compareTo(MAX_TOP_SCORER_ODDS) > 0) {
            return MAX_TOP_SCORER_ODDS;
        }
        return odds;
    }

    public boolean isEligibleTopScorerCandidate(Player player) {
        return TOP_SCORER_POSITIONS.contains(player.getPosition()) && !isUnavailable(player);
    }
}

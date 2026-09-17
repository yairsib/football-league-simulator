package com.footballsim.service;

import com.footballsim.dto.MatchResponse;
import com.footballsim.dto.RoundResponse;
import com.footballsim.entity.Match;
import com.footballsim.entity.MatchEvent;
import com.footballsim.entity.Player;
import com.footballsim.entity.Round;
import com.footballsim.entity.Team;
import com.footballsim.enums.MatchEventType;
import com.footballsim.enums.MatchStatus;
import com.footballsim.enums.PlayerStatus;
import com.footballsim.enums.Position;
import com.footballsim.enums.RoundStatus;
import com.footballsim.repository.MatchEventRepository;
import com.footballsim.repository.MatchRepository;
import com.footballsim.repository.PlayerRepository;
import com.footballsim.repository.RoundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

@Service
public class SimulationService {

    /** Per-team, per-match chance of a new simulation-generated injury. Kept low and simple. */
    private static final double INJURY_PROBABILITY = 0.10;
    private static final int MIN_INJURY_DURATION = 1;
    private static final int MAX_INJURY_DURATION = 5;
    private static final String[] INJURY_DESCRIPTIONS = {
            "Muscle injury", "Ankle injury", "Knee injury", "Hamstring injury", "Knock"
    };

    /** Per-team, per-match chance of a red card; capped at one red card per team per match. */
    private static final double RED_CARD_PROBABILITY = 0.08;
    /** Substitution constraints: 2-5 per team, minutes 46-85. */
    private static final int MIN_SUBSTITUTIONS = 2;
    private static final int MAX_SUBSTITUTIONS = 5;
    private static final int SUB_MINUTE_MIN = 46;
    private static final int SUB_MINUTE_MAX = 85;
    /** Chance that a goal is followed by an assist. */
    private static final double ASSIST_PROBABILITY = 0.75;
    /** Bounded attack reduction applied to a team that has a player sent off: 15% + up to 10% more = 15%-25%. */
    private static final double RED_CARD_MIN_REDUCTION = 0.15;
    private static final double RED_CARD_REDUCTION_RANGE = 0.10;

    private final MatchRepository matchRepository;
    private final RoundRepository roundRepository;
    private final PlayerRepository playerRepository;
    private final MatchEventRepository matchEventRepository;
    private final OddsService oddsService;
    private final BetService betService;
    private final LineupService lineupService;
    private Random random = new Random();

    /** Test-only seam: lets unit tests inject a deterministic Random to avoid flaky assertions on injury/event rolls. */
    void setRandom(Random random) {
        this.random = random;
    }

    public SimulationService(MatchRepository matchRepository,
                             RoundRepository roundRepository,
                             PlayerRepository playerRepository,
                             MatchEventRepository matchEventRepository,
                             OddsService oddsService,
                             BetService betService,
                             LineupService lineupService) {
        this.matchRepository = matchRepository;
        this.roundRepository = roundRepository;
        this.playerRepository = playerRepository;
        this.matchEventRepository = matchEventRepository;
        this.oddsService = oddsService;
        this.betService = betService;
        this.lineupService = lineupService;
    }

    /**
     * Prices and freezes every betting market for the match (1X2 + handicap) from one probability
     * vector. Called once when betting opens; the stored values are what users see and what bets
     * are booked at — they are never recomputed until the next season reset.
     */
    private void priceAndFreezeMarkets(Match match) {
        OddsService.MatchMarketOdds odds = oddsService.priceMatchMarkets(match);
        match.setHomeOdds(odds.home);
        match.setDrawOdds(odds.draw);
        match.setAwayOdds(odds.away);
        match.setHandicapHomeMinusOneOdds(odds.handicapHomeMinusOne);
        match.setHandicapDrawOdds(odds.handicapDraw);
        match.setHandicapAwayPlusOneOdds(odds.handicapAwayPlusOne);
    }

    @Transactional
    public List<MatchResponse> openBettingForRound(int roundNumber) {
        Round round = roundRepository.findByRoundNumber(roundNumber)
                .orElseThrow(() -> new IllegalArgumentException("Round " + roundNumber + " not found"));

        if (round.getStatus() != RoundStatus.NOT_STARTED) {
            throw new IllegalStateException("Round " + roundNumber + " cannot open betting (status: " + round.getStatus() + ")");
        }

        requirePreviousRoundsFinished(roundNumber);

        List<Match> matches = matchRepository.findByRound_RoundNumberOrderByIdAsc(roundNumber);
        if (matches.isEmpty()) {
            throw new IllegalStateException("No matches found for round " + roundNumber);
        }

        for (Match match : matches) {
            priceAndFreezeMarkets(match);
            match.setBettingOpen(true);
            match.setStatus(MatchStatus.BETTING_OPEN);
        }

        round.setStatus(RoundStatus.OPEN_FOR_BETS);
        round.setStartedAt(LocalDateTime.now());

        return matches.stream().map(MatchResponse::from).toList();
    }

    @Transactional
    public MatchResponse simulateMatch(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match " + matchId + " not found"));

        if (match.getStatus() == MatchStatus.FINISHED) {
            throw new IllegalStateException("Match " + matchId + " is already finished");
        }

        Round round = match.getRound();
        if (round.getStatus() == RoundStatus.NOT_STARTED) {
            throw new IllegalStateException("Round " + round.getRoundNumber() + " must be open for betting before its matches can be simulated.");
        }
        requirePreviousRoundsFinished(round.getRoundNumber());

        if (match.getHomeOdds() == null) {
            priceAndFreezeMarkets(match);
        }

        finishMatch(match);

        return MatchResponse.from(match);
    }

    @Transactional
    public RoundResponse simulateRound(int roundNumber) {
        Round round = roundRepository.findByRoundNumber(roundNumber)
                .orElseThrow(() -> new IllegalArgumentException("Round " + roundNumber + " not found"));

        if (round.getStatus() == RoundStatus.FINISHED) {
            throw new IllegalStateException("Round " + roundNumber + " is already finished");
        }
        if (round.getStatus() == RoundStatus.NOT_STARTED) {
            throw new IllegalStateException("Round " + roundNumber + " must be open for betting before it can be simulated.");
        }

        requirePreviousRoundsFinished(roundNumber);

        List<Match> matches = matchRepository.findByRound_RoundNumberAndStatusNotOrderByIdAsc(roundNumber, MatchStatus.FINISHED);

        for (Match match : matches) {
            if (match.getStatus() == MatchStatus.FINISHED) continue;

            if (match.getHomeOdds() == null) {
                priceAndFreezeMarkets(match);
            }

            finishMatch(match);
        }

        round.setStatus(RoundStatus.FINISHED);
        round.setFinishedAt(LocalDateTime.now());

        betService.settleSeasonBets();

        return RoundResponse.from(round);
    }

    private void requirePreviousRoundsFinished(int roundNumber) {
        if (roundNumber > 1 && roundRepository.existsByRoundNumberLessThanAndStatusNot(roundNumber, RoundStatus.FINISHED)) {
            throw new IllegalStateException("Previous rounds must be finished before starting this round.");
        }
    }

    private void generateScore(Match match, double[] probs) {
        double rand = random.nextDouble();
        double homeWinCutoff = probs[0];
        double drawCutoff = probs[0] + probs[1];

        if (rand < homeWinCutoff) {
            int homeGoals = 1 + random.nextInt(4);
            match.setHomeGoals(homeGoals);
            match.setAwayGoals(random.nextInt(homeGoals));
        } else if (rand < drawCutoff) {
            int goals = random.nextInt(4);
            match.setHomeGoals(goals);
            match.setAwayGoals(goals);
        } else {
            int awayGoals = 1 + random.nextInt(4);
            match.setHomeGoals(random.nextInt(awayGoals));
            match.setAwayGoals(awayGoals);
        }
    }

    private void applyTeamStats(Match match) {
        Team home = match.getHomeTeam();
        Team away = match.getAwayTeam();
        int hg = match.getHomeGoals();
        int ag = match.getAwayGoals();

        home.setPlayed(home.getPlayed() + 1);
        home.setGoalsFor(home.getGoalsFor() + hg);
        home.setGoalsAgainst(home.getGoalsAgainst() + ag);

        away.setPlayed(away.getPlayed() + 1);
        away.setGoalsFor(away.getGoalsFor() + ag);
        away.setGoalsAgainst(away.getGoalsAgainst() + hg);

        if (hg > ag) {
            home.setWins(home.getWins() + 1);
            home.setPoints(home.getPoints() + 3);
            away.setLosses(away.getLosses() + 1);
            applyResultToForm(home, true);
            applyResultToForm(away, false);
        } else if (hg == ag) {
            home.setDraws(home.getDraws() + 1);
            home.setPoints(home.getPoints() + 1);
            away.setDraws(away.getDraws() + 1);
            away.setPoints(away.getPoints() + 1);
        } else {
            away.setWins(away.getWins() + 1);
            away.setPoints(away.getPoints() + 3);
            home.setLosses(home.getLosses() + 1);
            applyResultToForm(away, true);
            applyResultToForm(home, false);
        }
    }

    private void applyResultToForm(Team team, boolean won) {
        int skillDelta = won ? 1 : -1;
        int moraleDelta = won ? 1 : -1;
        team.setSkillLevel(clamp(team.getSkillLevel() + skillDelta, 40, 100));
        team.setMorale(clamp(team.getMorale() + moraleDelta, 0, 10));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Orchestrates everything that happens once a match's score is decided: builds each team's
     * effective matchday lineup (the SAME lineup the Lineups page shows), rolls red cards among
     * the starting XI (before the score is generated, so they can genuinely affect it), generates
     * the score, applies team/league stats, progresses each team's existing injuries/suspensions,
     * applies any new red cards (after progression, so a freshly-issued suspension is never
     * decremented in the same match it was created), generates substitution events from the
     * effective bench, generates goalscorer/assist events with minute-based eligibility
     * (respecting sub/red-card timing), and settles bets.
     */
    private void finishMatch(Match match) {
        Team home = match.getHomeTeam();
        Team away = match.getAwayTeam();

        // One source of truth for who plays: the effective lineup (unavailable starters replaced,
        // injured/suspended players excluded) — identical to GET /api/matches/{id}/lineups.
        LineupService.EffectiveLineup homeLineup = matchdayLineup(home);
        LineupService.EffectiveLineup awayLineup = matchdayLineup(away);

        // A red card can only go to a player who is actually on the pitch: the starting XI.
        Player homeRedCardPlayer = maybeSelectRedCardPlayer(homeLineup.starters);
        Player awayRedCardPlayer = maybeSelectRedCardPlayer(awayLineup.starters);
        // Assign red card minutes upfront so minute-based eligibility can use them
        int homeRedCardMinute = homeRedCardPlayer != null ? 1 + random.nextInt(90) : -1;
        int awayRedCardMinute = awayRedCardPlayer != null ? 1 + random.nextInt(90) : -1;

        double[] probs = oddsService.computeProbabilities(home, away, match.getWeatherCondition(), match.getWeatherImpact());
        probs = applyRedCardAdjustment(probs, homeRedCardPlayer != null, awayRedCardPlayer != null);
        generateScore(match, probs);

        match.setStatus(MatchStatus.FINISHED);
        match.setBettingOpen(false);
        applyTeamStats(match);

        progressTeamInjuries(home);
        progressTeamInjuries(away);

        applyRedCard(match, home, homeRedCardPlayer, homeRedCardMinute);
        applyRedCard(match, away, awayRedCardPlayer, awayRedCardMinute);

        // Starting XI (on pitch at kick-off) and bench come straight from the effective lineup.
        List<Player> homeStarters = new ArrayList<>(homeLineup.starters);
        List<Player> homeBench    = new ArrayList<>(homeLineup.substitutes);
        List<Player> awayStarters = new ArrayList<>(awayLineup.starters);
        List<Player> awayBench    = new ArrayList<>(awayLineup.substitutes);

        // Generate substitutions (minute-aware, respects GK rules and red card timing)
        List<MatchEvent> homeSubs = generateSubstitutions(match, home, homeStarters, homeBench, homeRedCardPlayer, homeRedCardMinute);
        List<MatchEvent> awaySubs = generateSubstitutions(match, away, awayStarters, awayBench, awayRedCardPlayer, awayRedCardMinute);

        // Generate goal events with minute-based player eligibility
        generateGoalEvents(match, home, homeStarters, homeSubs, homeRedCardPlayer, homeRedCardMinute, match.getHomeGoals());
        generateGoalEvents(match, away, awayStarters, awaySubs, awayRedCardPlayer, awayRedCardMinute, match.getAwayGoals());

        betService.settleBetsForMatch(match);
        betService.settleSeasonBets();
    }

    /**
     * The effective matchday lineup for a team — the single source of truth shared with the
     * Lineups endpoint (PlayerService uses the same squad query and the same LineupService call).
     * Injured/suspended players are excluded; missing starters are replaced by the best-fit
     * available player so the XI is complete whenever enough eligible players exist.
     */
    LineupService.EffectiveLineup matchdayLineup(Team team) {
        List<Player> squad = playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(team.getId());
        return lineupService.generateEffectiveLineup(squad);
    }

    /**
     * Each team independently has an 8% chance of one red card in the match. The player is drawn
     * from the effective starting XI only (someone actually on the pitch); the minute is assigned
     * by the caller. Package-private for deterministic tests.
     */
    Player maybeSelectRedCardPlayer(List<Player> startingXI) {
        if (startingXI.isEmpty() || random.nextDouble() >= RED_CARD_PROBABILITY) {
            return null;
        }
        return startingXI.get(random.nextInt(startingXI.size()));
    }

    /**
     * Reduces a red-carded team's win probability by a bounded 15%-25%, transferring most of it
     * to the opponent's win probability (and a little to the draw), so the red card meaningfully
     * — but not overwhelmingly — affects the score that's about to be generated.
     */
    private double[] applyRedCardAdjustment(double[] probs, boolean homeRedCard, boolean awayRedCard) {
        if (!homeRedCard && !awayRedCard) {
            return probs;
        }

        double home = probs[0];
        double draw = probs[1];
        double away = probs[2];

        if (homeRedCard) {
            double reduction = home * (RED_CARD_MIN_REDUCTION + random.nextDouble() * RED_CARD_REDUCTION_RANGE);
            home -= reduction;
            away += reduction * 0.7;
            draw += reduction * 0.3;
        }
        if (awayRedCard) {
            double reduction = away * (RED_CARD_MIN_REDUCTION + random.nextDouble() * RED_CARD_REDUCTION_RANGE);
            away -= reduction;
            home += reduction * 0.7;
            draw += reduction * 0.3;
        }

        home = Math.max(home, 0.01);
        draw = Math.max(draw, 0.01);
        away = Math.max(away, 0.01);
        double total = home + draw + away;
        return new double[]{home / total, draw / total, away / total};
    }

    /** Applies a previously-decided red card at the given minute: stats, suspension, status change, and the MatchEvent. */
    void applyRedCard(Match match, Team team, Player player, int minute) {
        if (player == null) {
            return;
        }
        player.setRedCards(player.getRedCards() + 1);
        player.setStatus(PlayerStatus.SUSPENDED);
        player.setSuspensionMatchesRemaining(1);

        MatchEvent event = new MatchEvent();
        event.setMatch(match);
        event.setTeam(team);
        event.setPlayer(player);
        event.setEventType(MatchEventType.RED_CARD);
        event.setMinute(minute);
        event.setDescription(player.getFullName() + " received a red card");
        matchEventRepository.save(event);
    }

    /**
     * For each goal in the score, picks a minute (1-90), computes which players are active at
     * that minute (respecting substitution and red-card timings), then selects a weighted scorer
     * and optional assister. Same-minute tie-break order: RED_CARD → SUBSTITUTION → GOAL,
     * meaning both a red card and subs at minute M are applied before goal eligibility at M.
     */
    void generateGoalEvents(Match match, Team team,
                             List<Player> initialStarters,
                             List<MatchEvent> subs,
                             Player redCardPlayer, int redCardMinute,
                             Integer goals) {
        if (goals == null || goals <= 0 || initialStarters.isEmpty()) return;

        Set<Player> startingXI = new HashSet<>(initialStarters);
        for (int i = 0; i < goals; i++) {
            int minute = 1 + random.nextInt(90);
            List<Player> active = computeActiveAtMinute(initialStarters, subs, redCardPlayer, redCardMinute, minute);
            if (active.isEmpty()) continue;

            Player scorer = selectWeighted(active, p -> scorerWeight(p, startingXI.contains(p)));
            if (scorer == null) continue;
            scorer.setGoals(scorer.getGoals() + 1);

            Player assistPlayer = null;
            if (random.nextDouble() < ASSIST_PROBABILITY) {
                Player finalScorer = scorer;
                List<Player> assistCandidates = active.stream()
                        .filter(p -> p != finalScorer)
                        .collect(Collectors.toList());
                assistPlayer = selectWeighted(assistCandidates, SimulationService::assistWeight);
                if (assistPlayer != null) {
                    assistPlayer.setAssists(assistPlayer.getAssists() + 1);
                }
            }

            MatchEvent event = new MatchEvent();
            event.setMatch(match);
            event.setTeam(team);
            event.setPlayer(scorer);
            event.setAssistPlayer(assistPlayer);
            event.setEventType(MatchEventType.GOAL);
            event.setMinute(minute);
            event.setDescription(buildGoalDescription(scorer, assistPlayer));
            matchEventRepository.save(event);
        }
    }

    /**
     * Returns which players are active on the pitch at the given minute.
     * Substitutions and red cards that occurred at or before that minute are applied.
     * (Tie-break at the same minute: RED_CARD then SUBSTITUTION both happen before GOAL.)
     */
    List<Player> computeActiveAtMinute(List<Player> initialStarters, List<MatchEvent> subs,
                                        Player redCardPlayer, int redCardMinute, int minute) {
        List<Player> active = new ArrayList<>(initialStarters);
        for (MatchEvent sub : subs) {
            if (sub.getMinute() <= minute) {
                active.remove(sub.getPlayerOut());
                if (!active.contains(sub.getPlayerIn())) {
                    active.add(sub.getPlayerIn());
                }
            }
        }
        if (redCardPlayer != null && redCardMinute <= minute) {
            active.remove(redCardPlayer);
        }
        return active;
    }

    /**
     * Generates 2-5 substitution events for one team. playerOut is from the current on-pitch
     * list; playerIn comes from the bench. GK-for-GK and field-for-field rules apply; a field
     * player may replace a GK only when no bench GK is available (emergency exception).
     *
     * Timeline consistency with red cards: the player selected for this match's red card is
     * NEVER chosen as playerOut (a substituted-off player cannot later be sent off), stays on the
     * pitch until the card minute, and is removed from the on-pitch list once that minute is
     * reached (a sent-off player cannot later be substituted). No substitution is synthesised
     * for the sent-off player — the team simply plays on with one fewer.
     */
    List<MatchEvent> generateSubstitutions(Match match, Team team,
                                            List<Player> initialOnPitch,
                                            List<Player> bench,
                                            Player redCardPlayer, int redCardMinute) {
        if (initialOnPitch.isEmpty() || bench.isEmpty()) return List.of();

        int numSubs = MIN_SUBSTITUTIONS + random.nextInt(MAX_SUBSTITUTIONS - MIN_SUBSTITUTIONS + 1);
        numSubs = Math.min(numSubs, Math.min(bench.size(), initialOnPitch.size()));

        List<Integer> subMinutes = new ArrayList<>(numSubs);
        for (int i = 0; i < numSubs; i++) {
            subMinutes.add(SUB_MINUTE_MIN + random.nextInt(SUB_MINUTE_MAX - SUB_MINUTE_MIN + 1));
        }
        Collections.sort(subMinutes);

        List<Player> currentOnPitch = new ArrayList<>(initialOnPitch);
        List<Player> remainingBench = new ArrayList<>(bench);
        Set<Long> subbedOutIds = new HashSet<>();
        Set<Long> subbedInIds = new HashSet<>();
        boolean redCardApplied = false;
        List<MatchEvent> result = new ArrayList<>();

        for (int minute : subMinutes) {
            if (redCardPlayer != null && !redCardApplied && redCardMinute <= minute) {
                currentOnPitch.remove(redCardPlayer);
                redCardApplied = true;
            }

            List<Player> eligibleOut = currentOnPitch.stream()
                    .filter(p -> !subbedOutIds.contains(p.getId()))
                    .filter(p -> p != redCardPlayer)
                    .collect(Collectors.toList());
            if (eligibleOut.isEmpty()) break;

            Player playerOut = eligibleOut.get(random.nextInt(eligibleOut.size()));
            boolean outIsGK = playerOut.getPosition() == Position.GK;

            List<Player> eligibleIn;
            if (outIsGK) {
                eligibleIn = remainingBench.stream()
                        .filter(p -> p.getPosition() == Position.GK && !subbedInIds.contains(p.getId()))
                        .collect(Collectors.toList());
                if (eligibleIn.isEmpty()) {
                    // Emergency: no bench GK available — allow any bench player
                    eligibleIn = remainingBench.stream()
                            .filter(p -> !subbedInIds.contains(p.getId()))
                            .collect(Collectors.toList());
                }
            } else {
                eligibleIn = remainingBench.stream()
                        .filter(p -> p.getPosition() != Position.GK && !subbedInIds.contains(p.getId()))
                        .collect(Collectors.toList());
            }

            if (eligibleIn.isEmpty()) continue;

            Player playerIn = eligibleIn.get(random.nextInt(eligibleIn.size()));

            subbedOutIds.add(playerOut.getId());
            subbedInIds.add(playerIn.getId());
            currentOnPitch.remove(playerOut);
            currentOnPitch.add(playerIn);
            remainingBench.remove(playerIn);

            MatchEvent event = new MatchEvent();
            event.setMatch(match);
            event.setTeam(team);
            event.setPlayerOut(playerOut);
            event.setPlayerIn(playerIn);
            event.setEventType(MatchEventType.SUBSTITUTION);
            event.setMinute(minute);
            event.setDescription(team.getName() + ": " + playerOut.getFullName() + " out, " + playerIn.getFullName() + " in");
            matchEventRepository.save(event);
            result.add(event);
        }

        return result;
    }

    /** Weighted random pick from candidates using the given per-player weight function. */
    private Player selectWeighted(List<Player> candidates, ToDoubleFunction<Player> weightFn) {
        if (candidates.isEmpty()) {
            return null;
        }
        double totalWeight = candidates.stream().mapToDouble(weightFn).sum();
        if (totalWeight <= 0) {
            return candidates.get(random.nextInt(candidates.size()));
        }
        double pick = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (Player candidate : candidates) {
            cumulative += weightFn.applyAsDouble(candidate);
            if (pick < cumulative) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    /** ST/LW/RW score most often, then AM/CM/DM, then defenders; GK almost never; rating and starting the match raise the odds. */
    private static double scorerWeight(Player p, boolean startedMatch) {
        double base = switch (p.getPosition()) {
            case ST -> 10.0;
            case LW, RW -> 8.0;
            case AM -> 6.0;
            case CM -> 4.0;
            case DM -> 2.5;
            case CB, LB, RB -> 1.5;
            case GK -> 0.1;
        };
        double ratingFactor = Math.max(p.getRating(), 1) / 70.0;
        double starterMultiplier = startedMatch ? 1.5 : 1.0;
        return base * ratingFactor * starterMultiplier;
    }

    /** AM/wingers/CM/full-backs assist most often; rating raises the odds; GK almost never. */
    private static double assistWeight(Player p) {
        double base = switch (p.getPosition()) {
            case AM -> 8.0;
            case LW, RW -> 7.0;
            case CM, RB, LB -> 6.0;
            case ST, DM -> 3.0;
            case CB -> 1.5;
            case GK -> 0.1;
        };
        double ratingFactor = Math.max(p.getRating(), 1) / 70.0;
        return base * ratingFactor;
    }

    private static String buildGoalDescription(Player scorer, Player assistPlayer) {
        if (assistPlayer != null) {
            return scorer.getFullName() + " scored, assisted by " + assistPlayer.getFullName();
        }
        return scorer.getFullName() + " scored";
    }

    /**
     * Progresses one team's existing injuries and suspensions after it completes a match:
     * decrements remaining counts (clearing them and recomputing status on recovery), then rolls
     * a small chance of one new simulation-generated injury. Must run BEFORE any new red card from
     * this same match is applied, so a freshly-issued suspension is never decremented immediately.
     */
    void progressTeamInjuries(Team team) {
        List<Player> players = playerRepository.findByTeam_IdOrderByLineupOrderAscIdAsc(team.getId());
        if (players.isEmpty()) {
            return;
        }

        for (Player player : players) {
            boolean changed = false;
            if (player.getStatus() == PlayerStatus.INJURED && player.getInjuryMatchesRemaining() > 0) {
                player.setInjuryMatchesRemaining(player.getInjuryMatchesRemaining() - 1);
                changed = true;
            }
            if (player.getStatus() == PlayerStatus.SUSPENDED && player.getSuspensionMatchesRemaining() > 0) {
                player.setSuspensionMatchesRemaining(player.getSuspensionMatchesRemaining() - 1);
                changed = true;
            }
            if (changed) {
                recomputeStatusAfterProgression(player);
            }
        }

        if (random.nextDouble() < INJURY_PROBABILITY) {
            generateNewInjury(players);
        }

        long currentlyInjured = players.stream()
                .filter(p -> p.getStatus() == PlayerStatus.INJURED && p.getInjuryMatchesRemaining() > 0)
                .count();
        team.setInjuries((int) currentlyInjured);
    }

    /**
     * After decrementing, a player who still has matches remaining on either count keeps the
     * matching status (suspension takes priority, per the rule that an injured player who finishes
     * a suspension stays INJURED until their injury also clears); otherwise they become FIT.
     */
    private void recomputeStatusAfterProgression(Player player) {
        if (player.getSuspensionMatchesRemaining() > 0) {
            player.setStatus(PlayerStatus.SUSPENDED);
        } else if (player.getInjuryMatchesRemaining() > 0) {
            player.setStatus(PlayerStatus.INJURED);
        } else {
            player.setStatus(PlayerStatus.FIT);
            player.setInjuryDescription(null);
            player.setInjuryMatchesTotal(0);
        }
    }

    private void generateNewInjury(List<Player> players) {
        List<Player> candidates = players.stream()
                .filter(p -> p.getStatus() == PlayerStatus.FIT && (p.isStarter() || p.isSubstitute()))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            candidates = players.stream()
                    .filter(p -> p.getStatus() == PlayerStatus.FIT)
                    .collect(Collectors.toList());
        }
        if (candidates.isEmpty()) {
            return;
        }

        Player injured = candidates.get(random.nextInt(candidates.size()));
        int duration = MIN_INJURY_DURATION + random.nextInt(MAX_INJURY_DURATION - MIN_INJURY_DURATION + 1);
        injured.setStatus(PlayerStatus.INJURED);
        injured.setInjuryDescription(INJURY_DESCRIPTIONS[random.nextInt(INJURY_DESCRIPTIONS.length)]);
        injured.setInjuryMatchesRemaining(duration);
        injured.setInjuryMatchesTotal(duration);
    }
}

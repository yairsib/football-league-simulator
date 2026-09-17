package com.footballsim.service;

import com.footballsim.dto.SeasonResetRequest;
import com.footballsim.dto.SeasonResetResponse;
import com.footballsim.entity.Bet;
import com.footballsim.entity.Match;
import com.footballsim.entity.Player;
import com.footballsim.entity.Round;
import com.footballsim.entity.Team;
import com.footballsim.entity.User;
import com.footballsim.enums.BetStatus;
import com.footballsim.enums.MatchStatus;
import com.footballsim.enums.PlayerStatus;
import com.footballsim.enums.RoundStatus;
import com.footballsim.enums.WeatherCondition;
import com.footballsim.repository.BetRepository;
import com.footballsim.repository.BetSelectionRepository;
import com.footballsim.repository.MatchEventRepository;
import com.footballsim.repository.MatchRepository;
import com.footballsim.repository.PlayerRepository;
import com.footballsim.repository.RoundRepository;
import com.footballsim.repository.TeamRepository;
import com.footballsim.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class SeasonResetService {

    static final BigDecimal STARTING_BALANCE = new BigDecimal("1000.00");

    private final MatchEventRepository matchEventRepository;
    private final BetSelectionRepository betSelectionRepository;
    private final BetRepository betRepository;
    private final MatchRepository matchRepository;
    private final RoundRepository roundRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;
    private final Random random = new Random();

    public SeasonResetService(MatchEventRepository matchEventRepository,
                              BetSelectionRepository betSelectionRepository,
                              BetRepository betRepository,
                              MatchRepository matchRepository,
                              RoundRepository roundRepository,
                              TeamRepository teamRepository,
                              PlayerRepository playerRepository,
                              UserRepository userRepository) {
        this.matchEventRepository = matchEventRepository;
        this.betSelectionRepository = betSelectionRepository;
        this.betRepository = betRepository;
        this.matchRepository = matchRepository;
        this.roundRepository = roundRepository;
        this.teamRepository = teamRepository;
        this.playerRepository = playerRepository;
        this.userRepository = userRepository;
    }

    /**
     * Resets the season to a clean "before Round 1" state.
     *
     * Safe under any partial state: no schedule, no bets, no data imported — all
     * produce zero counts in the response, not exceptions.
     *
     * Balance contract:
     *   - resetUserBalances=false (default): OPEN bet stakes are refunded to each user
     *     before deletion so no money is lost; all other balances are preserved.
     *   - resetUserBalances=true: after cleanup, every user's balance is set to the
     *     starting balance (1000), superseding the refunds.
     *
     * Team baseline contract:
     *   - Every team's skillLevel/morale are restored to the PERSISTED season baseline
     *     (Team.baselineSkillLevel / baselineMorale) fixed when the league was imported —
     *     the randomised starting skill of that season, not the raw league-data number.
     *     Simulation drifts these fields by ±1 per result, so a reset must undo that drift.
     *   - A reset never rerolls the baseline: two resets return identical values. Only a
     *     fresh pre-season league import creates a new baseline.
     *   - The baselines are verified BEFORE any mutation. If any current team has no
     *     persisted baseline, the reset aborts with an IllegalStateException and nothing
     *     is modified. Season reset is a destructive admin operation — it must never
     *     report success with partially restored data.
     *
     * Never touches: user records, roles, passwords, OTP sessions, email settings,
     * team/player identity (names, IDs, crests, squads, formations).
     */
    @Transactional
    public SeasonResetResponse resetSeason(SeasonResetRequest request) {
        if (!request.isConfirm()) {
            throw new IllegalArgumentException(
                    "Season reset requires confirm=true in the request body.");
        }

        // 0. Verify every team has a persisted season baseline before touching anything.
        //    Any failure here aborts the reset with nothing modified (transaction rolled back).
        verifyPersistedBaselines();

        SeasonResetResponse r = new SeasonResetResponse();

        // 1. Refund OPEN bet stakes so no user loses money unfairly.
        //    (If resetUserBalances=true, these are overridden in step 7 anyway.)
        refundOpenBets(r);

        // 2. Delete match events (FK → matches, players — must go before matches).
        r.setMatchEventsDeleted((int) matchEventRepository.count());
        matchEventRepository.deleteAll();

        // 3. Delete bet selections (FK → bets, matches) then bets.
        //    Deleting selections explicitly before bets avoids relying on JPA cascade
        //    behaviour with bulk deletes, which is safer across JPA implementations.
        List<Bet> allBets = betRepository.findAll();
        r.setBetsDeleted(allBets.size());
        betSelectionRepository.deleteAll();
        betRepository.deleteAll();

        // 4. Reset schedule — delete+regenerate or update in-place.
        //    Returns the current team list for use in step 5.
        List<Team> teams = resetSchedule(request, r);

        // 5. Zero team season stats (standings, goals, injuries) and restore each team's
        //    skillLevel/morale to its persisted season baseline (simulation drifts both by ±1
        //    per result). Never changes name, formation, displayName, IDs, or the baseline itself.
        int baselinesRestored = 0;
        for (Team team : teams) {
            team.setPlayed(0);
            team.setWins(0);
            team.setDraws(0);
            team.setLosses(0);
            team.setGoalsFor(0);
            team.setGoalsAgainst(0);
            team.setPoints(0);
            team.setInjuries(0);

            if (team.getBaselineSkillLevel() == null || team.getBaselineMorale() == null) {
                // Guarded by step 0; only reachable if the team set changed mid-transaction.
                throw new IllegalStateException("Season reset aborted: team '" + team.getName()
                        + "' has no persisted starting baseline. The transaction has been rolled back.");
            }
            team.setSkillLevel(team.getBaselineSkillLevel());
            team.setMorale(team.getBaselineMorale());
            baselinesRestored++;
        }
        teamRepository.saveAll(teams);
        r.setTeamsReset(teams.size());
        r.setTeamBaselinesRestored(baselinesRestored);

        // 6. Zero player season stats and clear simulation-generated injuries/suspensions.
        //    Never changes fullName, position, rating, jersey, starter/substitute/lineupOrder.
        List<Player> players = playerRepository.findAll();
        for (Player player : players) {
            player.setGoals(0);
            player.setAssists(0);
            player.setRedCards(0);
            player.setSuspensionMatchesRemaining(0);
            player.setStatus(PlayerStatus.FIT);
            player.setInjuryDescription(null);
            player.setInjuredUntilRound(null);
            player.setInjuryMatchesRemaining(0);
            player.setInjuryMatchesTotal(0);
        }
        playerRepository.saveAll(players);
        r.setPlayersReset(players.size());

        // 7. Optionally reset every user's balance to the starting balance.
        //    Roles and passwords are never modified.
        if (request.isResetUserBalances()) {
            List<User> users = userRepository.findAll();
            for (User user : users) {
                user.setBalance(STARTING_BALANCE);
            }
            userRepository.saveAll(users);
            r.setUsersBalanceReset(users.size());
        } else {
            r.setUsersBalanceReset(0);
        }

        r.setMessage(buildMessage(r, request));
        return r;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Checks that every current team carries a persisted season baseline (set at import time).
     * Runs before any mutation so a failure leaves the season completely untouched.
     */
    private void verifyPersistedBaselines() {
        List<String> missing = teamRepository.findAll().stream()
                .filter(t -> t.getBaselineSkillLevel() == null || t.getBaselineMorale() == null)
                .map(t -> t.getName() == null ? "<unnamed team>" : t.getName())
                .sorted()
                .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Season reset aborted: no persisted starting baseline (skill/morale) for team(s) "
                    + missing + ". Re-import the league data in a pre-season state. No season data was modified.");
        }
    }

    private void refundOpenBets(SeasonResetResponse r) {
        List<Bet> openBets = betRepository.findAll().stream()
                .filter(b -> b.getStatus() == BetStatus.OPEN)
                .collect(Collectors.toList());
        if (openBets.isEmpty()) {
            r.setOpenBetsRefunded(0);
            return;
        }

        Map<Long, BigDecimal> refundByUserId = new HashMap<>();
        for (Bet bet : openBets) {
            refundByUserId.merge(bet.getUser().getId(), bet.getAmount(), BigDecimal::add);
        }

        List<User> users = userRepository.findAll();
        for (User user : users) {
            BigDecimal refund = refundByUserId.get(user.getId());
            if (refund != null) {
                user.setBalance(user.getBalance().add(refund));
            }
        }
        userRepository.saveAll(users);
        r.setOpenBetsRefunded(openBets.size());
    }

    /**
     * Resets the schedule according to the request flag.
     * Returns the full team list (used by the caller to reset team stats).
     */
    private List<Team> resetSchedule(SeasonResetRequest request, SeasonResetResponse r) {
        if (request.isRegenerateSchedule()) {
            r.setMatchesReset((int) matchRepository.count());
            r.setRoundsReset((int) roundRepository.count());
            matchRepository.deleteAll();
            matchRepository.flush();
            roundRepository.deleteAll();
            roundRepository.flush();

            List<Team> teams = teamRepository.findAll();
            if (teams.size() >= 2) {
                generateFreshSchedule(teams);
                r.setScheduleRegenerated(true);
            } else {
                r.setScheduleRegenerated(false);
            }
            return teams;
        } else {
            // In-place reset: same fixture structure, all results/state wiped.
            List<Match> matches = matchRepository.findAll();
            for (Match match : matches) {
                match.setStatus(MatchStatus.SCHEDULED);
                match.setBettingOpen(false);
                match.setHomeGoals(null);
                match.setAwayGoals(null);
                match.setHomeOdds(null);
                match.setDrawOdds(null);
                match.setAwayOdds(null);
                match.setHandicapHomeMinusOneOdds(null);
                match.setHandicapDrawOdds(null);
                match.setHandicapAwayPlusOneOdds(null);
            }
            matchRepository.saveAll(matches);
            r.setMatchesReset(matches.size());

            List<Round> rounds = roundRepository.findAll();
            for (Round round : rounds) {
                round.setStatus(RoundStatus.NOT_STARTED);
                round.setStartedAt(null);
                round.setFinishedAt(null);
            }
            roundRepository.saveAll(rounds);
            r.setRoundsReset(rounds.size());
            r.setScheduleRegenerated(false);

            return teamRepository.findAll();
        }
    }

    /**
     * Generates a complete double round-robin schedule for the given teams.
     * Algorithm mirrors AdminService.generateSchedule() so a reset produces an
     * equivalent fresh schedule. Keep both in sync if the algorithm changes.
     */
    private void generateFreshSchedule(List<Team> teams) {
        int n = teams.size();
        Map<Long, Team> teamById = teams.stream()
                .collect(Collectors.toMap(Team::getId, t -> t));
        Long[] ids = teams.stream().map(Team::getId).toArray(Long[]::new);

        Long fixed = ids[0];
        Long[] circle = Arrays.copyOfRange(ids, 1, n);

        List<Long[][]> firstHalf = new ArrayList<>();
        for (int r = 0; r < n - 1; r++) {
            Long[][] pairs = new Long[n / 2][2];
            pairs[0][0] = fixed;
            pairs[0][1] = circle[n - 2];
            for (int i = 0; i < (n - 2) / 2; i++) {
                pairs[i + 1][0] = circle[i];
                pairs[i + 1][1] = circle[n - 3 - i];
            }
            firstHalf.add(pairs);
            Long last = circle[n - 2];
            System.arraycopy(circle, 0, circle, 1, n - 2);
            circle[0] = last;
        }

        List<Long[][]> allRounds = new ArrayList<>(firstHalf);
        for (Long[][] fhr : firstHalf) {
            Long[][] shr = new Long[n / 2][2];
            for (int i = 0; i < n / 2; i++) {
                shr[i][0] = fhr[i][1];
                shr[i][1] = fhr[i][0];
            }
            allRounds.add(shr);
        }

        for (int r = 0; r < allRounds.size(); r++) {
            Round round = new Round();
            round.setRoundNumber(r + 1);
            round.setStatus(RoundStatus.NOT_STARTED);
            roundRepository.save(round);

            for (Long[] pair : allRounds.get(r)) {
                Match match = new Match();
                match.setRound(round);
                match.setHomeTeam(teamById.get(pair[0]));
                match.setAwayTeam(teamById.get(pair[1]));
                match.setStatus(MatchStatus.SCHEDULED);
                match.setBettingOpen(false);
                assignWeather(match);
                matchRepository.save(match);
            }
        }
    }

    private void assignWeather(Match match) {
        WeatherCondition[] conditions = WeatherCondition.values();
        WeatherCondition condition = conditions[random.nextInt(conditions.length)];
        int impact = switch (condition) {
            case CLEAR -> 0;
            case RAIN  -> -(1 + random.nextInt(2));
            case WIND  -> random.nextInt(3) - 1;
            case HOT   -> -1;
            case COLD  -> random.nextInt(3) - 1;
            case STORM -> -(2 + random.nextInt(2));
        };
        match.setWeatherCondition(condition);
        match.setWeatherImpact(impact);
    }

    private String buildMessage(SeasonResetResponse r, SeasonResetRequest req) {
        StringBuilder sb = new StringBuilder("Season reset complete.");
        sb.append(" Deleted: ").append(r.getMatchEventsDeleted()).append(" event(s), ")
          .append(r.getBetsDeleted()).append(" bet(s).");
        if (req.isRegenerateSchedule()) {
            if (r.isScheduleRegenerated()) {
                sb.append(" Schedule regenerated (").append(r.getRoundsReset())
                  .append(" old round(s) removed).");
            } else {
                sb.append(" No teams found — schedule not regenerated.");
            }
        } else {
            sb.append(" ").append(r.getMatchesReset()).append(" match(es) and ")
              .append(r.getRoundsReset()).append(" round(s) reset in place.");
        }
        sb.append(" ").append(r.getTeamsReset()).append(" team(s) and ")
          .append(r.getPlayersReset()).append(" player(s) reset.");
        sb.append(" Skill/morale restored to baseline for ")
          .append(r.getTeamBaselinesRestored()).append(" team(s).");
        if (r.getOpenBetsRefunded() > 0) {
            sb.append(" ").append(r.getOpenBetsRefunded()).append(" open bet(s) refunded.");
        }
        if (req.isResetUserBalances()) {
            sb.append(" ").append(r.getUsersBalanceReset())
              .append(" user balance(s) reset to 1000.");
        }
        return sb.toString();
    }
}

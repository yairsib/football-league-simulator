package com.footballsim.service;

import com.footballsim.dto.AdminOverviewResponse;
import com.footballsim.dto.DataImportResponse;
import com.footballsim.entity.Bet;
import com.footballsim.entity.Match;
import com.footballsim.entity.Player;
import com.footballsim.entity.Round;
import com.footballsim.entity.Team;
import com.footballsim.enums.BetStatus;
import com.footballsim.enums.MatchStatus;
import com.footballsim.enums.RoundStatus;
import com.footballsim.enums.WeatherCondition;
import com.footballsim.repository.BetRepository;
import com.footballsim.repository.MatchRepository;
import com.footballsim.repository.PlayerRepository;
import com.footballsim.repository.RoundRepository;
import com.footballsim.repository.TeamRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final String REAL_DATA_FILE = "data/ligat-haal-2025-2026.json";

    private final TeamRepository teamRepository;
    private final RoundRepository roundRepository;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final BetRepository betRepository;
    private final BetService betService;
    private final LeagueDataImportService leagueDataImportService;
    private final Random random = new Random();

    public AdminService(TeamRepository teamRepository,
                        RoundRepository roundRepository,
                        MatchRepository matchRepository,
                        PlayerRepository playerRepository,
                        BetRepository betRepository,
                        BetService betService,
                        LeagueDataImportService leagueDataImportService) {
        this.teamRepository = teamRepository;
        this.roundRepository = roundRepository;
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.betRepository = betRepository;
        this.betService = betService;
        this.leagueDataImportService = leagueDataImportService;
    }

    /**
     * Seeds teams. Prefers importing the real Israeli Premier League 2025/2026
     * data file when present (so the league reflects real teams/skill levels),
     * and falls back to the simple random demo seed otherwise.
     */
    @Transactional
    public String seedTeams() {
        if (teamRepository.count() > 0) {
            throw new IllegalStateException("Teams already seeded.");
        }

        if (new ClassPathResource(REAL_DATA_FILE).exists()) {
            DataImportResponse summary = leagueDataImportService.importData();
            if (summary.getErrors().isEmpty()) {
                return "Seeded " + summary.getTeamsProcessed() + " teams from real-data file ("
                        + summary.getPlayersProcessed() + " players).";
            }
            // Real-data file exists but is invalid — fall through to the demo seed below.
        }

        String[] names = {
            "Maccabi Tel Aviv", "Hapoel Tel Aviv", "Beitar Jerusalem", "Hapoel Beer Sheva",
            "Maccabi Haifa", "Bnei Sakhnin", "Maccabi Netanya", "Hapoel Haifa"
        };

        for (String name : names) {
            Team team = new Team();
            team.setName(name);
            int initialSkill = 60 + random.nextInt(31);
            int initialMorale = random.nextInt(11);
            team.setSkillLevel(initialSkill);
            team.setMorale(initialMorale);
            // The random starting values are this season's persisted baseline (restored by Season Reset).
            team.setBaselineSkillLevel(initialSkill);
            team.setBaselineMorale(initialMorale);
            team.setInjuries(random.nextInt(6));
            teamRepository.save(team);
        }

        return "Seeded 8 teams.";
    }

    @Transactional
    public String generateSchedule() {
        if (matchRepository.count() > 0) {
            throw new IllegalStateException("Schedule already exists.");
        }

        List<Team> teams = teamRepository.findAll();
        if (teams.size() < 2 || teams.size() % 2 != 0) {
            throw new IllegalStateException("Need an even number of teams (at least 2). Run /api/admin/seed first.");
        }

        int n = teams.size();
        Map<Long, Team> teamById = teams.stream().collect(Collectors.toMap(Team::getId, t -> t));
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
        for (Long[][] firstHalfRound : firstHalf) {
            Long[][] secondHalfRound = new Long[n / 2][2];
            for (int i = 0; i < n / 2; i++) {
                secondHalfRound[i][0] = firstHalfRound[i][1];
                secondHalfRound[i][1] = firstHalfRound[i][0];
            }
            allRounds.add(secondHalfRound);
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

        int totalMatches = allRounds.size() * (n / 2);
        return "Generated " + allRounds.size() + " rounds with " + totalMatches + " matches.";
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse getAdminOverview() {
        List<Team> teams = teamRepository.findAll();
        List<Player> players = playerRepository.findAll();
        List<Round> rounds = roundRepository.findAll();
        List<Match> matches = matchRepository.findAll();
        List<Bet> allBets = betRepository.findAll();

        // ── Data state ────────────────────────────────────────────────────────
        int teamsCount = teams.size();
        int playersCount = players.size();

        Map<Long, Long> playerCountByTeam = players.stream()
                .collect(Collectors.groupingBy(p -> p.getTeam().getId(), Collectors.counting()));
        int completeSquadsCount = (int) teams.stream()
                .filter(t -> playerCountByTeam.getOrDefault(t.getId(), 0L) >= 18)
                .count();
        int missingSquadsCount = (int) teams.stream()
                .filter(t -> playerCountByTeam.getOrDefault(t.getId(), 0L) == 0)
                .count();

        boolean scheduleGenerated = !rounds.isEmpty();
        int totalRounds = rounds.size();
        int totalMatches = matches.size();

        // ── Round state ───────────────────────────────────────────────────────
        Integer currentRound = rounds.stream()
                .filter(r -> r.getStatus() == RoundStatus.OPEN_FOR_BETS
                          || r.getStatus() == RoundStatus.FINISHED)
                .mapToInt(Round::getRoundNumber)
                .max()
                .stream().boxed().findFirst().orElse(null);

        // next round that is NOT_STARTED where all previous are FINISHED
        Integer nextRoundToOpen = rounds.stream()
                .filter(r -> r.getStatus() == RoundStatus.NOT_STARTED)
                .mapToInt(Round::getRoundNumber)
                .min()
                .stream().boxed().findFirst().orElse(null);
        if (nextRoundToOpen != null) {
            final int nr = nextRoundToOpen;
            boolean canOpen = rounds.stream()
                    .filter(r -> r.getRoundNumber() < nr)
                    .allMatch(r -> r.getStatus() == RoundStatus.FINISHED);
            if (!canOpen) nextRoundToOpen = null;
        }

        Integer nextRoundToSimulate = rounds.stream()
                .filter(r -> r.getStatus() == RoundStatus.OPEN_FOR_BETS)
                .mapToInt(Round::getRoundNumber)
                .min()
                .stream().boxed().findFirst().orElse(null);

        Map<String, Long> roundsByStatus = rounds.stream()
                .collect(Collectors.groupingBy(r -> r.getStatus().name(), Collectors.counting()));

        // ── Betting state ─────────────────────────────────────────────────────
        int openBetsCount = (int) allBets.stream()
                .filter(b -> b.getStatus() == BetStatus.OPEN).count();
        int settledBetsCount = (int) allBets.stream()
                .filter(b -> b.getStatus() == BetStatus.WON || b.getStatus() == BetStatus.LOST).count();
        int cancelledBetsCount = (int) allBets.stream()
                .filter(b -> b.getStatus() == BetStatus.CANCELLED).count();
        boolean seasonBetsOpen = betService.isSeasonBettingOpen();
        String seasonBetsStatusReason = seasonBetsOpen
                ? "Season bets are open before Round 1 is played"
                : "Season bets are locked because Round 1 has started";

        // ── Recommended actions ───────────────────────────────────────────────
        List<String> recommendedActions = new ArrayList<>();
        if (teams.isEmpty()) {
            recommendedActions.add("Import league data via Setup → Seed Teams or Import League Data");
        } else if (!scheduleGenerated) {
            recommendedActions.add("Generate schedule via Setup → Generate Schedule");
        } else {
            if (nextRoundToOpen != null) {
                recommendedActions.add("Open betting for Round " + nextRoundToOpen
                        + " via Round Actions → Open Betting");
            }
            if (nextRoundToSimulate != null) {
                recommendedActions.add("Simulate Round " + nextRoundToSimulate
                        + " via Round Actions → Simulate Round");
            }
            if (nextRoundToOpen == null && nextRoundToSimulate == null) {
                boolean allFinished = rounds.stream()
                        .allMatch(r -> r.getStatus() == RoundStatus.FINISHED);
                if (allFinished) {
                    recommendedActions.add("Season complete — all rounds have been simulated");
                } else {
                    // NOT_STARTED rounds exist but their predecessor rounds aren't finished yet
                    rounds.stream()
                            .filter(r -> r.getStatus() == RoundStatus.NOT_STARTED)
                            .mapToInt(Round::getRoundNumber)
                            .min()
                            .ifPresent(n -> recommendedActions.add(
                                    "Previous rounds must finish before Round " + n + " can be opened"));
                }
            }
        }

        // ── Assemble ──────────────────────────────────────────────────────────
        AdminOverviewResponse response = new AdminOverviewResponse();
        response.setTeamsCount(teamsCount);
        response.setPlayersCount(playersCount);
        response.setCompleteSquadsCount(completeSquadsCount);
        response.setMissingSquadsCount(missingSquadsCount);
        response.setScheduleGenerated(scheduleGenerated);
        response.setTotalRounds(totalRounds);
        response.setTotalMatches(totalMatches);
        response.setCurrentRound(currentRound);
        response.setNextRoundToOpen(nextRoundToOpen);
        response.setNextRoundToSimulate(nextRoundToSimulate);
        response.setRoundsByStatus(roundsByStatus);
        response.setOpenBetsCount(openBetsCount);
        response.setSettledBetsCount(settledBetsCount);
        response.setCancelledBetsCount(cancelledBetsCount);
        response.setSeasonBetsOpen(seasonBetsOpen);
        response.setSeasonBetsStatusReason(seasonBetsStatusReason);
        response.setLeagueDataImportAllowed(!leagueDataImportService.isSeasonStarted());
        response.setRecommendedActions(recommendedActions);
        return response;
    }

    /**
     * Assigns a random visible weather condition and a small impact magnitude.
     * Impact is kept small and condition-appropriate so weather nudges odds/results
     * without overpowering team skill (see OddsService).
     */
    private void assignWeather(Match match) {
        WeatherCondition[] conditions = WeatherCondition.values();
        WeatherCondition condition = conditions[random.nextInt(conditions.length)];

        int impact = switch (condition) {
            case CLEAR -> 0;
            case RAIN -> -(1 + random.nextInt(2));      // -1 or -2: fewer goals
            case WIND -> random.nextInt(3) - 1;          // -1..+1: more randomness, little net effect
            case HOT -> -1;                              // slightly reduced tempo
            case COLD -> random.nextInt(3) - 1;          // -1..+1: small random effect
            case STORM -> -(2 + random.nextInt(2));      // -2 or -3: fewer goals, more randomness
        };

        match.setWeatherCondition(condition);
        match.setWeatherImpact(impact);
    }
}

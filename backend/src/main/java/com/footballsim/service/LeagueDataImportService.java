package com.footballsim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballsim.dto.DataImportResponse;
import com.footballsim.dto.DataValidationResponse;
import com.footballsim.dto.LeagueSeedData;
import com.footballsim.dto.PlayerSeedData;
import com.footballsim.dto.TeamSeedData;
import com.footballsim.dto.TeamSquadSummary;
import com.footballsim.entity.Player;
import com.footballsim.entity.Team;
import com.footballsim.enums.MatchStatus;
import com.footballsim.enums.PlayerStatus;
import com.footballsim.enums.Position;
import com.footballsim.enums.RoundStatus;
import com.footballsim.repository.BetRepository;
import com.footballsim.repository.MatchEventRepository;
import com.footballsim.repository.MatchRepository;
import com.footballsim.repository.PlayerRepository;
import com.footballsim.repository.RoundRepository;
import com.footballsim.repository.TeamRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads, validates, and imports the structured real-data JSON file
 * (resources/data/ligat-haal-2025-2026.json) describing Israeli Premier
 * League 2025/2026 teams and squads.
 *
 * The import is an upsert: existing teams are matched by name and existing
 * players by (team, fullName). Nothing is deleted, and nothing is invented —
 * teams whose players[] is empty stay empty and are reported as warnings.
 * Entries whose dataSource starts with "EXAMPLE" (template/placeholder data)
 * are always skipped.
 *
 * Season-start rule: importing (re)initialises every team's starting skill/morale and every
 * player's season statistics, so it is only permitted in a clean pre-season state — see
 * {@link #isSeasonStarted()}. Once betting has opened, a match has finished, events exist, or
 * any bet exists, the import is rejected (409) until the admin resets the season.
 *
 * Initial skill: the JSON skill is the realistic centre for each team; the actual starting
 * skill is JSON skill + a uniform random offset in [-INITIAL_SKILL_VARIATION, +INITIAL_SKILL_VARIATION],
 * clamped to the global range. That value is persisted as the team's season baseline.
 */
@Service
public class LeagueDataImportService {

    private static final String DATA_FILE = "data/ligat-haal-2025-2026.json";
    private static final int REQUIRED_TEAM_COUNT = 14;
    private static final int MIN_SKILL_LEVEL = 40;
    private static final int MAX_SKILL_LEVEL = 100;
    private static final int MIN_MORALE = 0;
    private static final int MAX_MORALE = 10;
    private static final int REQUIRED_STARTERS = 11;
    private static final int MIN_SUBSTITUTES = 7;
    private static final int MIN_PLAYERS_FOR_COMPLETE_SQUAD = 18;
    /** Random starting-skill variation around the JSON value (±3 keeps the realistic strength order). */
    static final int INITIAL_SKILL_VARIATION = 3;

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final RoundRepository roundRepository;
    private final MatchRepository matchRepository;
    private final BetRepository betRepository;
    private final MatchEventRepository matchEventRepository;
    private final ObjectMapper objectMapper;
    private Random random = new Random();

    public LeagueDataImportService(TeamRepository teamRepository,
                                   PlayerRepository playerRepository,
                                   RoundRepository roundRepository,
                                   MatchRepository matchRepository,
                                   BetRepository betRepository,
                                   MatchEventRepository matchEventRepository) {
        this.teamRepository = teamRepository;
        this.playerRepository = playerRepository;
        this.roundRepository = roundRepository;
        this.matchRepository = matchRepository;
        this.betRepository = betRepository;
        this.matchEventRepository = matchEventRepository;
        this.objectMapper = new ObjectMapper();
    }

    /** Test-only seam: inject a deterministic Random for the initial-skill variation. */
    void setRandom(Random random) {
        this.random = random;
    }

    /**
     * True once any season activity exists: a round that is not NOT_STARTED, a match that is not
     * SCHEDULED, any match event, or any bet (open, settled, or cancelled). A proper Season Reset
     * returns all of these to their pre-season state.
     */
    public boolean isSeasonStarted() {
        return roundRepository.existsByStatusNot(RoundStatus.NOT_STARTED)
                || matchRepository.existsByStatusNot(MatchStatus.SCHEDULED)
                || matchEventRepository.count() > 0
                || betRepository.count() > 0;
    }

    private void requirePreSeason() {
        if (isSeasonStarted()) {
            throw new IllegalStateException(
                    "League data can only be imported before the season starts (no opened rounds, results, events, or bets). "
                    + "Use Reset Season first to return to a clean pre-season state.");
        }
    }

    /** JSON skill plus a bounded random offset, clamped to the valid global range. */
    int randomisedInitialSkill(int referenceSkill) {
        int offset = random.nextInt(2 * INITIAL_SKILL_VARIATION + 1) - INITIAL_SKILL_VARIATION;
        return Math.max(MIN_SKILL_LEVEL, Math.min(MAX_SKILL_LEVEL, referenceSkill + offset));
    }

    /** Reads and parses the bundled real-data JSON file from the classpath. */
    public LeagueSeedData loadFromClasspath() {
        ClassPathResource resource = new ClassPathResource(DATA_FILE);
        if (!resource.exists()) {
            throw new IllegalStateException("Real-data file not found on classpath: " + DATA_FILE);
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, LeagueSeedData.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read/parse " + DATA_FILE + ": " + e.getMessage(), e);
        }
    }

    /** Validates the bundled real-data file and returns a structured result. */
    public DataValidationResponse validate() {
        LeagueSeedData data = loadFromClasspath();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<TeamSquadSummary> squadSummaries = new ArrayList<>();
        validate(data, errors, warnings, squadSummaries);
        return DataValidationResponse.of(errors.isEmpty(), errors, warnings, squadSummaries);
    }

    /**
     * Validates the bundled real-data file, then imports it if there are no errors.
     * Rejected (IllegalStateException → 409) once the season has started; the check runs before
     * anything is read or written, so a rejected import changes nothing.
     */
    @Transactional
    public DataImportResponse importData() {
        requirePreSeason();
        LeagueSeedData data = loadFromClasspath();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        validate(data, errors, warnings, new ArrayList<>());

        if (!errors.isEmpty()) {
            return DataImportResponse.of(0, 0, warnings, errors);
        }

        int teamsProcessed = 0;
        int playersProcessed = 0;

        for (TeamSeedData teamSeed : data.getTeams()) {
            if (teamSeed.isExample()) {
                continue;
            }

            Team team = teamRepository.findByName(teamSeed.getName()).orElseGet(Team::new);
            team.setName(teamSeed.getName());
            team.setDisplayName(blankToNull(teamSeed.getDisplayName()));
            int initialSkill = randomisedInitialSkill(teamSeed.getSkillLevel());
            team.setSkillLevel(initialSkill);
            team.setMorale(teamSeed.getMorale());
            // Persist this season's actual starting values — Season Reset restores these, never rerolls.
            team.setBaselineSkillLevel(initialSkill);
            team.setBaselineMorale(teamSeed.getMorale());
            team.setDefaultFormation(teamSeed.getDefaultFormation());
            Team savedTeam = teamRepository.save(team);
            teamsProcessed++;

            for (PlayerSeedData playerSeed : teamSeed.getPlayers()) {
                if (playerSeed.isExample()) {
                    continue;
                }
                upsertPlayer(savedTeam, playerSeed);
                playersProcessed++;
            }
        }

        return DataImportResponse.of(teamsProcessed, playersProcessed, warnings, errors);
    }

    /**
     * Imports a player from real-world source data. Real-world injury/suspension status is
     * NEVER imported — injuries in this app are generated exclusively by the match simulation,
     * so every imported player starts FIT regardless of what the source data says about their
     * real-life fitness. The seed file's own injury fields (injuryDescription, injuredUntilRound,
     * injuryMatchesRemaining/Total) are ignored on import for this reason. Likewise, every
     * imported player always starts with goals/assists/redCards/suspensionMatchesRemaining = 0 —
     * all player statistics and match events in this project are simulation-generated only.
     */
    private void upsertPlayer(Team team, PlayerSeedData seed) {
        Player player = playerRepository.findByTeam_IdAndFullName(team.getId(), seed.getFullName())
                .orElseGet(Player::new);
        player.setTeam(team);
        player.setFullName(seed.getFullName());
        player.setPosition(Position.valueOf(seed.getPosition()));
        player.setJerseyNumber(seed.getJerseyNumber());
        player.setRating(seed.getRating());
        player.setStatus(PlayerStatus.FIT);
        player.setInjuryDescription(null);
        player.setInjuredUntilRound(null);
        player.setInjuryMatchesRemaining(0);
        player.setInjuryMatchesTotal(0);
        player.setGoals(0);
        player.setAssists(0);
        player.setRedCards(0);
        player.setSuspensionMatchesRemaining(0);
        player.setStarter(seed.isStarter());
        player.setSubstitute(seed.isSubstitute());
        player.setLineupOrder(seed.getLineupOrder());
        player.setDataSource(seed.getDataSource());
        playerRepository.save(player);
    }

    // ---- validation -------------------------------------------------------

    private void validate(LeagueSeedData data, List<String> errors, List<String> warnings,
                          List<TeamSquadSummary> squadSummaries) {
        List<TeamSeedData> teams = data.getTeams();

        long realTeamCount = teams.stream().filter(t -> !t.isExample()).count();
        if (realTeamCount != REQUIRED_TEAM_COUNT) {
            errors.add("Expected exactly " + REQUIRED_TEAM_COUNT + " teams but found " + realTeamCount + ".");
        }

        Set<String> seenNames = new HashSet<>();
        for (TeamSeedData team : teams) {
            if (team.isExample()) {
                continue;
            }
            squadSummaries.add(validateTeam(team, seenNames, errors, warnings));
        }
    }

    private TeamSquadSummary validateTeam(TeamSeedData team, Set<String> seenNames, List<String> errors, List<String> warnings) {
        int errorsBeforeTeam = errors.size();
        String name = team.getName();
        String label = (name == null || name.isBlank()) ? "<unnamed team>" : name;

        if (name == null || name.isBlank()) {
            errors.add("Team name must not be blank.");
        } else if (!seenNames.add(name)) {
            errors.add("Duplicate team name: '" + name + "'.");
        }

        if (team.getSkillLevel() < MIN_SKILL_LEVEL || team.getSkillLevel() > MAX_SKILL_LEVEL) {
            errors.add("Team '" + label + "': skillLevel " + team.getSkillLevel()
                    + " must be between " + MIN_SKILL_LEVEL + " and " + MAX_SKILL_LEVEL + ".");
        }

        if (team.getMorale() < MIN_MORALE || team.getMorale() > MAX_MORALE) {
            errors.add("Team '" + label + "': morale " + team.getMorale()
                    + " must be between " + MIN_MORALE + " and " + MAX_MORALE + ".");
        }

        if (team.getDefaultFormation() == null || team.getDefaultFormation().isBlank()) {
            errors.add("Team '" + label + "': defaultFormation must not be blank.");
        }

        List<PlayerSeedData> players = team.getPlayers().stream().filter(p -> !p.isExample()).toList();
        if (players.isEmpty()) {
            warnings.add("Team '" + label + "': squad is missing (players[] is empty).");
            List<String> allPositions = Arrays.stream(Position.values()).map(Enum::name).toList();
            return TeamSquadSummary.missing(label, allPositions);
        }

        return validateSquad(label, players, errors, warnings, errorsBeforeTeam);
    }

    private TeamSquadSummary validateSquad(String teamLabel, List<PlayerSeedData> players, List<String> errors,
                                            List<String> warnings, int errorsBeforeTeam) {
        int starters = 0;
        int substitutes = 0;
        int unavailableCount = 0;
        int gkStarters = 0;
        boolean invalidOverlap = false;
        Set<Integer> starterLineupOrders = new HashSet<>();
        Set<Integer> duplicateLineupOrders = new TreeSet<>();
        Set<String> seenFullNames = new HashSet<>();
        Set<Integer> seenJerseyNumbers = new HashSet<>();
        Set<Integer> duplicateJerseyNumbers = new TreeSet<>();
        Set<Position> usedPositions = EnumSet.noneOf(Position.class);

        for (PlayerSeedData player : players) {
            String playerLabel = (player.getFullName() == null || player.getFullName().isBlank())
                    ? "<unnamed player>" : player.getFullName();

            if (player.getFullName() == null || player.getFullName().isBlank()) {
                errors.add("Team '" + teamLabel + "': player fullName must not be blank.");
            } else if (!seenFullNames.add(player.getFullName())) {
                errors.add("Team '" + teamLabel + "': duplicate player fullName '" + player.getFullName() + "'.");
            }

            Position position = parseEnum(Position.class, player.getPosition());
            if (position == null) {
                errors.add("Team '" + teamLabel + "': player '" + playerLabel + "' has invalid position '"
                        + player.getPosition() + "'.");
            } else {
                usedPositions.add(position);
            }

            PlayerStatus status = parseEnum(PlayerStatus.class, player.getStatus());
            if (status == null) {
                errors.add("Team '" + teamLabel + "': player '" + playerLabel + "' has invalid status '"
                        + player.getStatus() + "'.");
            }

            if (player.isStarter() && player.isSubstitute()) {
                errors.add("Team '" + teamLabel + "': player '" + playerLabel
                        + "' cannot be both starter and substitute.");
                invalidOverlap = true;
            }

            boolean unavailable = status == PlayerStatus.INJURED || status == PlayerStatus.SUSPENDED;
            if (unavailable && (player.isStarter() || player.isSubstitute())) {
                errors.add("Team '" + teamLabel + "': player '" + playerLabel
                        + "' is " + status + " and cannot be a starter or substitute.");
            }
            if (unavailable) {
                unavailableCount++;
            }

            if (player.isStarter() && !unavailable) {
                starters++;
                if (position == Position.GK) {
                    gkStarters++;
                }
                if (player.getLineupOrder() != null && !starterLineupOrders.add(player.getLineupOrder())) {
                    duplicateLineupOrders.add(player.getLineupOrder());
                }
            }
            if (player.isSubstitute() && !unavailable) {
                substitutes++;
            }

            if (player.getJerseyNumber() != null && !seenJerseyNumbers.add(player.getJerseyNumber())) {
                duplicateJerseyNumbers.add(player.getJerseyNumber());
            }
        }

        boolean hasExactly11Starters = starters == REQUIRED_STARTERS;
        boolean hasExactlyOneGK = gkStarters == 1;
        boolean hasAtLeast7Subs = substitutes >= MIN_SUBSTITUTES;

        if (!hasExactly11Starters) {
            warnings.add("Team '" + teamLabel + "': expected " + REQUIRED_STARTERS + " starters but found " + starters + ".");
        }
        if (!hasExactlyOneGK) {
            warnings.add("Team '" + teamLabel + "': expected exactly 1 starting GK but found " + gkStarters + ".");
        }
        if (!hasAtLeast7Subs) {
            warnings.add("Team '" + teamLabel + "': expected at least " + MIN_SUBSTITUTES
                    + " substitutes but found " + substitutes + ".");
        }
        if (!duplicateLineupOrders.isEmpty()) {
            warnings.add("Team '" + teamLabel + "': lineupOrder values among starters are not unique: "
                    + duplicateLineupOrders + ".");
        }
        if (!duplicateJerseyNumbers.isEmpty()) {
            warnings.add("Team '" + teamLabel + "': jersey numbers are not unique: " + duplicateJerseyNumbers + ".");
        }

        List<String> missingPositions = Arrays.stream(Position.values())
                .filter(p -> !usedPositions.contains(p))
                .map(Enum::name)
                .toList();

        boolean hasHardErrors = errors.size() > errorsBeforeTeam;
        String status;
        if (players.size() >= MIN_PLAYERS_FOR_COMPLETE_SQUAD && hasExactly11Starters && hasExactlyOneGK
                && hasAtLeast7Subs && !hasHardErrors) {
            status = "COMPLETE";
        } else {
            status = "PARTIAL";
        }

        return TeamSquadSummary.of(teamLabel, players.size(), starters, substitutes, unavailableCount,
                hasExactlyOneGK, hasAtLeast7Subs, hasExactly11Starters,
                new ArrayList<>(duplicateJerseyNumbers), new ArrayList<>(duplicateLineupOrders),
                invalidOverlap, missingPositions, status);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}

package com.footballsim.service;

import com.footballsim.dto.BetResponse;
import com.footballsim.dto.DashboardSummaryResponse;
import com.footballsim.entity.Bet;
import com.footballsim.entity.Match;
import com.footballsim.entity.Player;
import com.footballsim.entity.Round;
import com.footballsim.entity.Team;
import com.footballsim.entity.User;
import com.footballsim.enums.BetStatus;
import com.footballsim.enums.MatchStatus;
import com.footballsim.enums.Role;
import com.footballsim.enums.RoundStatus;
import com.footballsim.repository.BetRepository;
import com.footballsim.repository.MatchRepository;
import com.footballsim.repository.PlayerRepository;
import com.footballsim.repository.RoundRepository;
import com.footballsim.repository.TeamRepository;
import com.footballsim.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final BetRepository betRepository;
    private final MatchRepository matchRepository;
    private final RoundRepository roundRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final BetService betService;

    public DashboardService(UserRepository userRepository,
                            BetRepository betRepository,
                            MatchRepository matchRepository,
                            RoundRepository roundRepository,
                            TeamRepository teamRepository,
                            PlayerRepository playerRepository,
                            BetService betService) {
        this.userRepository = userRepository;
        this.betRepository = betRepository;
        this.matchRepository = matchRepository;
        this.roundRepository = roundRepository;
        this.teamRepository = teamRepository;
        this.playerRepository = playerRepository;
        this.betService = betService;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        boolean isAdmin = user.getRole() == Role.ADMIN;

        // ── Bet data ──────────────────────────────────────────────────────────
        List<Bet> allBets = betRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());

        int openBetsCount      = (int) allBets.stream().filter(b -> b.getStatus() == BetStatus.OPEN).count();
        int wonBetsCount       = (int) allBets.stream().filter(b -> b.getStatus() == BetStatus.WON).count();
        int lostBetsCount      = (int) allBets.stream().filter(b -> b.getStatus() == BetStatus.LOST).count();
        int cancelledBetsCount = (int) allBets.stream().filter(b -> b.getStatus() == BetStatus.CANCELLED).count();
        int settledBetsCount   = wonBetsCount + lostBetsCount;

        BigDecimal totalStakedOpen = allBets.stream()
                .filter(b -> b.getStatus() == BetStatus.OPEN)
                .map(Bet::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal potentialWinningsOpen = allBets.stream()
                .filter(b -> b.getStatus() == BetStatus.OPEN)
                .map(Bet::getPossibleWin)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalProfitSettled = allBets.stream()
                .filter(b -> b.getStatus() == BetStatus.WON || b.getStatus() == BetStatus.LOST)
                .map(b -> b.getProfit() != null ? b.getProfit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BetResponse> recentBets = allBets.stream()
                .limit(5)
                .map(BetResponse::from)
                .toList();

        // ── Round / match data ────────────────────────────────────────────────
        List<Round> rounds = roundRepository.findAll();
        int totalRounds = rounds.size();
        int finishedRoundsCount = (int) rounds.stream()
                .filter(r -> r.getStatus() == RoundStatus.FINISHED).count();

        Integer currentRoundNumber = rounds.stream()
                .filter(r -> r.getStatus() == RoundStatus.OPEN_FOR_BETS
                          || r.getStatus() == RoundStatus.FINISHED)
                .mapToInt(Round::getRoundNumber)
                .max()
                .stream().boxed().findFirst().orElse(null);

        Integer nextOpenRoundNumber = rounds.stream()
                .filter(r -> r.getStatus() == RoundStatus.OPEN_FOR_BETS)
                .mapToInt(Round::getRoundNumber)
                .min()
                .stream().boxed().findFirst().orElse(null);

        Integer nextRoundNumberToPlay = rounds.stream()
                .filter(r -> r.getStatus() == RoundStatus.NOT_STARTED)
                .mapToInt(Round::getRoundNumber)
                .min()
                .stream().boxed().findFirst().orElse(null);

        List<Match> allMatches = matchRepository.findAll();
        int totalMatches = allMatches.size();
        int finishedMatchesCount = (int) allMatches.stream()
                .filter(m -> m.getStatus() == MatchStatus.FINISHED).count();
        boolean hasOpenBettingMatches = allMatches.stream()
                .anyMatch(m -> m.getStatus() == MatchStatus.BETTING_OPEN);

        boolean seasonComplete = totalRounds > 0 && finishedRoundsCount == totalRounds;

        // ── Season bet status ─────────────────────────────────────────────────
        boolean seasonBetsOpen = betService.isSeasonBettingOpen();
        String seasonBetsStatusReason;
        if (seasonBetsOpen) {
            seasonBetsStatusReason = "Season bets are open before Round 1 is played";
        } else if (totalRounds == 0) {
            seasonBetsStatusReason = "No schedule generated yet";
        } else if (seasonComplete) {
            seasonBetsStatusReason = "Season is complete — all matches played";
        } else {
            seasonBetsStatusReason = "Season bets are locked because Round 1 has started";
        }

        // ── League highlights ─────────────────────────────────────────────────
        List<Team> teams = teamRepository.findAll();
        Team leader = teams.stream()
                .max(Comparator.comparingInt(Team::getPoints)
                        .thenComparingInt(t -> t.getGoalsFor() - t.getGoalsAgainst())
                        .thenComparingInt(Team::getGoalsFor)
                        .thenComparing(Comparator.comparing(Team::getName).reversed()))
                .orElse(null);

        List<Player> allPlayers = playerRepository.findAll();
        Player topScorer = allPlayers.isEmpty() ? null : allPlayers.stream()
                .max(Comparator.comparingInt(Player::getGoals)
                        .thenComparingInt(Player::getAssists)
                        .thenComparingInt(Player::getRating))
                .orElse(null);
        Player topAssister = allPlayers.isEmpty() ? null : allPlayers.stream()
                .max(Comparator.comparingInt(Player::getAssists)
                        .thenComparingInt(Player::getGoals)
                        .thenComparingInt(Player::getRating))
                .orElse(null);
        Player redCardLeader = allPlayers.isEmpty() ? null : allPlayers.stream()
                .max(Comparator.comparingInt(Player::getRedCards))
                .orElse(null);

        // ── Quick actions ─────────────────────────────────────────────────────
        boolean adminCanGenerateSchedule = isAdmin && !teams.isEmpty() && rounds.isEmpty();
        boolean adminCanOpenNextRound = isAdmin && nextRoundNumberToPlay != null && (
                nextRoundNumberToPlay == 1 || rounds.stream()
                        .filter(r -> r.getRoundNumber() < nextRoundNumberToPlay)
                        .allMatch(r -> r.getStatus() == RoundStatus.FINISHED)
        );
        boolean adminCanSimulateNextRound = isAdmin && nextOpenRoundNumber != null;

        // ── Assemble response ─────────────────────────────────────────────────
        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setBalance(user.getBalance());
        response.setOpenBetsCount(openBetsCount);
        response.setSettledBetsCount(settledBetsCount);
        response.setWonBetsCount(wonBetsCount);
        response.setLostBetsCount(lostBetsCount);
        response.setCancelledBetsCount(cancelledBetsCount);
        response.setTotalStakedOpen(totalStakedOpen);
        response.setTotalProfitSettled(totalProfitSettled);
        response.setPotentialWinningsOpen(potentialWinningsOpen);
        response.setRecentBets(recentBets);

        response.setTotalRounds(totalRounds);
        response.setCurrentRoundNumber(currentRoundNumber);
        response.setNextRoundNumberToPlay(nextRoundNumberToPlay);
        response.setFinishedRoundsCount(finishedRoundsCount);
        response.setTotalMatches(totalMatches);
        response.setFinishedMatchesCount(finishedMatchesCount);
        response.setNextOpenRoundNumber(nextOpenRoundNumber);
        response.setSeasonBetsOpen(seasonBetsOpen);
        response.setSeasonBetsStatusReason(seasonBetsStatusReason);
        response.setSeasonComplete(seasonComplete);

        if (leader != null) {
            response.setLeaderTeamName(leader.getName());
            response.setLeaderTeamId(leader.getId());
            response.setLeaderPoints(leader.getPoints());
        }
        if (topScorer != null && topScorer.getGoals() > 0) {
            response.setTopScorerName(topScorer.getFullName());
            response.setTopScorerTeam(topScorer.getTeam().getName());
            response.setTopScorerGoals(topScorer.getGoals());
        }
        if (topAssister != null && topAssister.getAssists() > 0) {
            response.setTopAssisterName(topAssister.getFullName());
            response.setTopAssisterTeam(topAssister.getTeam().getName());
            response.setTopAssisterAssists(topAssister.getAssists());
        }
        if (redCardLeader != null && redCardLeader.getRedCards() > 0) {
            response.setRedCardLeaderName(redCardLeader.getFullName());
            response.setRedCardLeaderTeam(redCardLeader.getTeam().getName());
            response.setRedCardLeaderCards(redCardLeader.getRedCards());
        }

        response.setCanPlaceSeasonBets(seasonBetsOpen);
        response.setCanPlaceMatchBets(hasOpenBettingMatches);
        response.setHasOpenBets(openBetsCount > 0);
        response.setHasFinishedMatches(finishedMatchesCount > 0);
        response.setAdminCanGenerateSchedule(adminCanGenerateSchedule);
        response.setAdminCanOpenNextRound(adminCanOpenNextRound);
        response.setAdminCanSimulateNextRound(adminCanSimulateNextRound);

        return response;
    }
}

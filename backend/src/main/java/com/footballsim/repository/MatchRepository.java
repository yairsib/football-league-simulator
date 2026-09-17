package com.footballsim.repository;

import com.footballsim.entity.Match;
import com.footballsim.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByRound_RoundNumberOrderByIdAsc(int roundNumber);

    List<Match> findByRound_RoundNumberAndStatusNotOrderByIdAsc(int roundNumber, MatchStatus status);

    List<Match> findByStatus(MatchStatus status);

    List<Match> findByBettingOpenTrue();

    boolean existsByRound_RoundNumber(int roundNumber);

    boolean existsByStatusNot(MatchStatus status);
}

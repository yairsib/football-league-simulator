package com.footballsim.repository;

import com.footballsim.entity.Bet;
import com.footballsim.enums.BetMarket;
import com.footballsim.enums.BetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BetRepository extends JpaRepository<Bet, Long> {

    List<Bet> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Bet> findByMatch_IdAndStatus(Long matchId, BetStatus status);

    boolean existsByUser_IdAndMatch_Id(Long userId, Long matchId);

    List<Bet> findByUser_IdAndStatusOrderByCreatedAtDesc(Long userId, BetStatus status);

    List<Bet> findByUser_IdAndStatusNotOrderByCreatedAtDesc(Long userId, BetStatus status);

    /** Used by season bet settlement to find all open CHAMPION or TOP_SCORER bets across all users. */
    List<Bet> findByMarketAndStatus(BetMarket market, BetStatus status);
}

package com.footballsim.repository;

import com.footballsim.entity.BetSelection;
import com.footballsim.enums.BetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BetSelectionRepository extends JpaRepository<BetSelection, Long> {

    List<BetSelection> findByMatch_IdAndBet_Status(Long matchId, BetStatus status);
}

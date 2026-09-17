package com.footballsim.repository;

import com.footballsim.entity.MatchEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchEventRepository extends JpaRepository<MatchEvent, Long> {

    List<MatchEvent> findByMatch_IdOrderByMinuteAscIdAsc(Long matchId);
}

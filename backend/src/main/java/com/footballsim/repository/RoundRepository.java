package com.footballsim.repository;

import com.footballsim.entity.Round;
import com.footballsim.enums.RoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoundRepository extends JpaRepository<Round, Long> {

    Optional<Round> findByRoundNumber(int roundNumber);

    boolean existsByRoundNumber(int roundNumber);

    boolean existsByRoundNumberLessThanAndStatusNot(int roundNumber, RoundStatus status);

    boolean existsByStatusNot(RoundStatus status);
}

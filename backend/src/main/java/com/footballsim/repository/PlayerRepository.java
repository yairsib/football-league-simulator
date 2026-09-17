package com.footballsim.repository;

import com.footballsim.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByTeam_IdOrderByLineupOrderAscIdAsc(Long teamId);

    Optional<Player> findByTeam_IdAndFullName(Long teamId, String fullName);
}

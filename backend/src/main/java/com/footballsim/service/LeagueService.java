package com.footballsim.service;

import com.footballsim.dto.LeagueTableResponse;
import com.footballsim.entity.Team;
import com.footballsim.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LeagueService {

    private final TeamRepository teamRepository;

    public LeagueService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public List<LeagueTableResponse> getLeagueTable() {
        List<Team> teams = new ArrayList<>(teamRepository.findAll());
        teams.sort(Comparator
                .comparingInt((Team t) -> -t.getPoints())
                .thenComparingInt((Team t) -> -(t.getGoalsFor() - t.getGoalsAgainst()))
                .thenComparingInt((Team t) -> -t.getGoalsFor())
                .thenComparing(Team::getName));

        List<LeagueTableResponse> table = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            table.add(LeagueTableResponse.from(i + 1, teams.get(i)));
        }
        return table;
    }
}

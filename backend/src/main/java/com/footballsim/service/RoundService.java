package com.footballsim.service;

import com.footballsim.dto.RoundResponse;
import com.footballsim.entity.Round;
import com.footballsim.repository.RoundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class RoundService {

    private final RoundRepository roundRepository;

    public RoundService(RoundRepository roundRepository) {
        this.roundRepository = roundRepository;
    }

    @Transactional(readOnly = true)
    public List<RoundResponse> getAllRounds() {
        return roundRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Round::getRoundNumber))
                .map(RoundResponse::from)
                .toList();
    }
}

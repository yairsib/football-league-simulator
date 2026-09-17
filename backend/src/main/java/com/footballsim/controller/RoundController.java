package com.footballsim.controller;

import com.footballsim.dto.RoundResponse;
import com.footballsim.service.RoundService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rounds")
public class RoundController {

    private final RoundService roundService;

    public RoundController(RoundService roundService) {
        this.roundService = roundService;
    }

    @GetMapping
    public ResponseEntity<List<RoundResponse>> getAllRounds() {
        return ResponseEntity.ok(roundService.getAllRounds());
    }
}

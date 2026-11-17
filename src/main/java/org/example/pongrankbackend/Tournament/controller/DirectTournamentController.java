package org.example.pongrankbackend.Tournament.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.pongrankbackend.Tournament.dto.DirectTournamentRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentMatchResponseDTO;
import org.example.pongrankbackend.Tournament.service.DirectTournamentService;
import org.example.pongrankbackend.Tournament.service.TournamentResultsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tournaments")
@RequiredArgsConstructor
public class DirectTournamentController {
    private final DirectTournamentService service;
    private final TournamentResultsService results;

    @PostMapping("/direct-elimination")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public TournamentResponseDTO publish(@Valid @RequestBody DirectTournamentRequestDTO request) {
        return service.publish(request);
    }

    @PostMapping("/direct-elimination/matches/{matchId}/withdraw")
    @PreAuthorize("isAuthenticated()")
    public TournamentMatchResponseDTO withdraw(@PathVariable Long matchId) {
        return results.withdraw(matchId);
    }
}

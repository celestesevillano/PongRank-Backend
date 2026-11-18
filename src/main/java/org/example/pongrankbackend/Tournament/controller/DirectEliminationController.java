package org.example.pongrankbackend.Tournament.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Tournament.dto.DirectEliminationCreateRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentMatchResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.example.pongrankbackend.Tournament.service.TournamentDirectEliminationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tournaments/direct-elimination")
public class DirectEliminationController {

    private final TournamentDirectEliminationService directEliminationService;

    public DirectEliminationController(TournamentDirectEliminationService directEliminationService) {
        this.directEliminationService = directEliminationService;
    }

    // Creates the tournament, registers the participants and publishes the whole bracket in one step
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TournamentResponseDTO> publish(@Valid @RequestBody DirectEliminationCreateRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(directEliminationService.publish(dto));
    }

    // The current player leaves the scheduled match identified by its Match id; the opponent advances by walkover
    @PostMapping("/matches/{matchId}/withdraw")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TournamentMatchResponseDTO> withdraw(@PathVariable Long matchId) {
        return ResponseEntity.ok(directEliminationService.withdraw(matchId));
    }
}

package org.example.pongrankbackend.Match.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.dto.*;
import org.example.pongrankbackend.Match.service.MatchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    // TODO: Extract creatorPlayerId from authenticated user via SecurityContext
    @PostMapping
    public ResponseEntity<MatchResponseDTO> createMatch(
            @RequestParam Long creatorPlayerId,
            @Valid @RequestBody MatchCreateRequestDTO dto) {
        MatchResponseDTO response = matchService.createMatch(creatorPlayerId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Listado general de partidos con paginación y ordenamiento.
     */
    @GetMapping
    public ResponseEntity<Page<MatchResponseDTO>> getAllMatches(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<MatchResponseDTO> response = matchService.getAllMatches(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{matchId}")
    public ResponseEntity<MatchDetailResponseDTO> getMatchById(@PathVariable Long matchId) {
        MatchDetailResponseDTO response = matchService.getMatchById(matchId);
        return ResponseEntity.ok(response);
    }

    /**
     * Historial de partidos de un jugador con paginación, ordenamiento y filtro opcional por estado.
     */
    @GetMapping("/player/{playerId}")
    public ResponseEntity<Page<MatchResponseDTO>> getMatchesByPlayer(
            @PathVariable Long playerId,
            @RequestParam(required = false) MatchStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<MatchResponseDTO> response = (status != null)
                ? matchService.getMatchesByPlayerAndStatus(playerId, status, pageable)
                : matchService.getMatchesByPlayer(playerId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Listado de partidos disputados en un torneo con paginación y orden cronológico descendente.
     */
    @GetMapping("/tournament/{tournamentId}")
    public ResponseEntity<Page<MatchResponseDTO>> getMatchesByTournament(
            @PathVariable Long tournamentId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<MatchResponseDTO> response = matchService.getMatchesByTournament(tournamentId, pageable);
        return ResponseEntity.ok(response);
    }

    // TODO: Extract submittingPlayerId from authenticated user via SecurityContext
    @PostMapping("/{matchId}/submit")
    public ResponseEntity<MatchDetailResponseDTO> submitScore(
            @PathVariable Long matchId,
            @RequestParam Long submittingPlayerId,
            @Valid @RequestBody MatchScoreSubmitDTO dto) {
        MatchDetailResponseDTO response = matchService.submitScore(matchId, submittingPlayerId, dto);
        return ResponseEntity.ok(response);
    }

    // TODO: Extract actingPlayerId from authenticated user via SecurityContext
    @PutMapping("/{matchId}/confirm")
    public ResponseEntity<MatchDetailResponseDTO> confirmMatch(
            @PathVariable Long matchId,
            @RequestParam Long actingPlayerId) {
        MatchDetailResponseDTO response = matchService.confirmMatch(matchId, actingPlayerId);
        return ResponseEntity.ok(response);
    }

    // TODO: Extract actingPlayerId from authenticated user via SecurityContext
    @PutMapping("/{matchId}/dispute")
    public ResponseEntity<MatchDetailResponseDTO> disputeMatch(
            @PathVariable Long matchId,
            @RequestParam Long actingPlayerId,
            @Valid @RequestBody MatchDisputeRequestDTO dto) {
        MatchDetailResponseDTO response = matchService.disputeMatch(matchId, actingPlayerId, dto);
        return ResponseEntity.ok(response);
    }

    // TODO: Extract actingPlayerId from authenticated user via SecurityContext
    @PutMapping("/{matchId}/cancel")
    public ResponseEntity<MatchResponseDTO> cancelMatch(
            @PathVariable Long matchId,
            @RequestParam Long actingPlayerId) {
        MatchResponseDTO response = matchService.cancelMatch(matchId, actingPlayerId);
        return ResponseEntity.ok(response);
    }
}

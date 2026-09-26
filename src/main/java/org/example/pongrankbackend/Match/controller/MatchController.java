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
import org.example.pongrankbackend.security.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MatchResponseDTO> createMatch(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody MatchCreateRequestDTO dto) {
        MatchResponseDTO response = matchService.createMatch(currentUser.getId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Partidos libres (LOCATION) abiertos, cercanos y de nivel similar al del jugador que busca.
     */
    @GetMapping("/open")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MatchResponseDTO>> getOpenLocationMatchesNearby(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam BigDecimal latitude,
            @RequestParam BigDecimal longitude) {
        List<MatchResponseDTO> response = matchService.getOpenLocationMatchesNearby(currentUser.getId(), latitude, longitude);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{matchId}/join")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MatchResponseDTO> joinOpenMatch(
            @PathVariable Long matchId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody MatchJoinRequestDTO dto) {
        MatchResponseDTO response = matchService.joinOpenMatch(currentUser.getId(), matchId, dto);
        return ResponseEntity.ok(response);
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

    @PostMapping("/{matchId}/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MatchDetailResponseDTO> submitScore(
            @PathVariable Long matchId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody MatchScoreSubmitDTO dto) {
        MatchDetailResponseDTO response = matchService.submitScore(matchId, currentUser.getId(), dto);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{matchId}/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MatchDetailResponseDTO> confirmMatch(
            @PathVariable Long matchId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        MatchDetailResponseDTO response = matchService.confirmMatch(matchId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{matchId}/dispute")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MatchDetailResponseDTO> disputeMatch(
            @PathVariable Long matchId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody MatchDisputeRequestDTO dto) {
        MatchDetailResponseDTO response = matchService.disputeMatch(matchId, currentUser.getId(), dto);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{matchId}/resolve-dispute")
    @PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<MatchDetailResponseDTO> resolveDispute(
            @PathVariable Long matchId,
            @Valid @RequestBody MatchDisputeResolutionDTO dto) {
        MatchDetailResponseDTO response = matchService.resolveDispute(matchId, dto);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{matchId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MatchResponseDTO> cancelMatch(
            @PathVariable Long matchId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        MatchResponseDTO response = matchService.cancelMatch(matchId, currentUser.getId());
        return ResponseEntity.ok(response);
    }
}

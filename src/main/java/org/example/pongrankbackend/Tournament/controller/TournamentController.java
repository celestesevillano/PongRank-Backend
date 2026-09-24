package org.example.pongrankbackend.Tournament.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Tournament.dto.GroupStandingsResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentCreateRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentMatchResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentOrderedPlayersRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentWalkoverRequestDTO;
import org.example.pongrankbackend.Tournament.service.TournamentService;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// WARNING: actingPlayerId is sent by the client and is NOT secure authentication. Local testing only until JWT is integrated.
@RestController
@RequestMapping("/api/v1/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;

    public TournamentController(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PostMapping
    public ResponseEntity<TournamentResponseDTO> createTournament(
            @RequestParam Long actingPlayerId,
            @Valid @RequestBody TournamentCreateRequestDTO dto) {
        TournamentResponseDTO response = tournamentService.createTournament(actingPlayerId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{tournamentId}")
    public ResponseEntity<TournamentResponseDTO> getTournamentById(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(tournamentService.getTournamentById(tournamentId));
    }

    @GetMapping("/clubs/{clubId}")
    public ResponseEntity<PageResponseDTO<TournamentResponseDTO>> getClubTournaments(
            @PathVariable Long clubId,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(tournamentService.getClubTournaments(clubId, page, size));
    }

    @GetMapping("/{tournamentId}/participants")
    public ResponseEntity<List<TournamentParticipantResponseDTO>> getParticipants(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(tournamentService.getParticipants(tournamentId));
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PostMapping("/{tournamentId}/participants")
    public ResponseEntity<TournamentParticipantResponseDTO> addParticipant(
            @PathVariable Long tournamentId,
            @RequestParam Long actingPlayerId,
            @Valid @RequestBody TournamentParticipantRequestDTO dto) {
        TournamentParticipantResponseDTO response = tournamentService.addParticipant(tournamentId, actingPlayerId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @DeleteMapping("/{tournamentId}/participants/{playerId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId,
            @RequestParam Long actingPlayerId) {
        tournamentService.removeParticipant(tournamentId, playerId, actingPlayerId);
        return ResponseEntity.noContent().build();
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PutMapping("/{tournamentId}/seeding")
    public ResponseEntity<List<TournamentParticipantResponseDTO>> updateSeeding(
            @PathVariable Long tournamentId,
            @RequestParam Long actingPlayerId,
            @Valid @RequestBody TournamentOrderedPlayersRequestDTO dto) {
        return ResponseEntity.ok(tournamentService.updateSeeding(tournamentId, actingPlayerId, dto));
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PostMapping("/{tournamentId}/start")
    public ResponseEntity<TournamentResponseDTO> startTournament(
            @PathVariable Long tournamentId,
            @RequestParam Long actingPlayerId) {
        return ResponseEntity.ok(tournamentService.startTournament(tournamentId, actingPlayerId));
    }

    @GetMapping("/{tournamentId}/matches")
    public ResponseEntity<List<TournamentMatchResponseDTO>> getMatches(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(tournamentService.getMatches(tournamentId));
    }

    @GetMapping("/{tournamentId}/groups")
    public ResponseEntity<List<GroupStandingsResponseDTO>> getGroupStandings(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(tournamentService.getGroupStandings(tournamentId));
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PutMapping("/{tournamentId}/groups/{groupNumber}/tie-resolution")
    public ResponseEntity<List<GroupStandingsResponseDTO>> resolveGroupTie(
            @PathVariable Long tournamentId,
            @PathVariable Integer groupNumber,
            @RequestParam Long actingPlayerId,
            @Valid @RequestBody TournamentOrderedPlayersRequestDTO dto) {
        return ResponseEntity.ok(tournamentService.resolveGroupTie(tournamentId, groupNumber, actingPlayerId, dto));
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PostMapping("/{tournamentId}/knockout")
    public ResponseEntity<TournamentResponseDTO> generateKnockout(
            @PathVariable Long tournamentId,
            @RequestParam Long actingPlayerId,
            @RequestParam(defaultValue = "false") boolean allowSameGroupMatches) {
        return ResponseEntity.ok(tournamentService.generateKnockout(tournamentId, actingPlayerId, allowSameGroupMatches));
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PostMapping("/{tournamentId}/matches/{tournamentMatchId}/walkover")
    public ResponseEntity<TournamentMatchResponseDTO> declareWalkover(
            @PathVariable Long tournamentId,
            @PathVariable Long tournamentMatchId,
            @RequestParam Long actingPlayerId,
            @Valid @RequestBody TournamentWalkoverRequestDTO dto) {
        return ResponseEntity.ok(tournamentService.declareWalkover(tournamentId, tournamentMatchId, actingPlayerId, dto));
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PostMapping("/{tournamentId}/sync-results")
    public ResponseEntity<TournamentResponseDTO> syncMatchResults(
            @PathVariable Long tournamentId,
            @RequestParam Long actingPlayerId) {
        return ResponseEntity.ok(tournamentService.syncMatchResults(tournamentId, actingPlayerId));
    }
}

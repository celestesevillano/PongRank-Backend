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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;

    public TournamentController(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TournamentResponseDTO> createTournament(@Valid @RequestBody TournamentCreateRequestDTO dto) {
        TournamentResponseDTO response = tournamentService.createTournament(dto);
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

    @PostMapping("/{tournamentId}/participants")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TournamentParticipantResponseDTO> addParticipant(
            @PathVariable Long tournamentId,
            @Valid @RequestBody TournamentParticipantRequestDTO dto) {
        TournamentParticipantResponseDTO response = tournamentService.addParticipant(tournamentId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{tournamentId}/participants/{playerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId) {
        tournamentService.removeParticipant(tournamentId, playerId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{tournamentId}/seeding")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TournamentParticipantResponseDTO>> updateSeeding(
            @PathVariable Long tournamentId,
            @Valid @RequestBody TournamentOrderedPlayersRequestDTO dto) {
        return ResponseEntity.ok(tournamentService.updateSeeding(tournamentId, dto));
    }

    @PostMapping("/{tournamentId}/start")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TournamentResponseDTO> startTournament(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(tournamentService.startTournament(tournamentId));
    }

    @GetMapping("/{tournamentId}/matches")
    public ResponseEntity<List<TournamentMatchResponseDTO>> getMatches(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(tournamentService.getMatches(tournamentId));
    }

    @GetMapping("/{tournamentId}/groups")
    public ResponseEntity<List<GroupStandingsResponseDTO>> getGroupStandings(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(tournamentService.getGroupStandings(tournamentId));
    }

    @PutMapping("/{tournamentId}/groups/{groupNumber}/tie-resolution")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<GroupStandingsResponseDTO>> resolveGroupTie(
            @PathVariable Long tournamentId,
            @PathVariable Integer groupNumber,
            @Valid @RequestBody TournamentOrderedPlayersRequestDTO dto) {
        return ResponseEntity.ok(tournamentService.resolveGroupTie(tournamentId, groupNumber, dto));
    }

    @PostMapping("/{tournamentId}/knockout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TournamentResponseDTO> generateKnockout(
            @PathVariable Long tournamentId,
            @RequestParam(defaultValue = "false") boolean allowSameGroupMatches) {
        return ResponseEntity.ok(tournamentService.generateKnockout(tournamentId, allowSameGroupMatches));
    }

    @PostMapping("/{tournamentId}/matches/{tournamentMatchId}/walkover")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TournamentMatchResponseDTO> declareWalkover(
            @PathVariable Long tournamentId,
            @PathVariable Long tournamentMatchId,
            @Valid @RequestBody TournamentWalkoverRequestDTO dto) {
        return ResponseEntity.ok(tournamentService.declareWalkover(tournamentId, tournamentMatchId, dto));
    }

    @PostMapping("/{tournamentId}/sync-results")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TournamentResponseDTO> syncMatchResults(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(tournamentService.syncMatchResults(tournamentId));
    }
}

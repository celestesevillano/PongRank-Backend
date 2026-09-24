package org.example.pongrankbackend.ClubMembership.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.ClubMembership.dto.ClubMembershipRequestDTO;
import org.example.pongrankbackend.ClubMembership.dto.ClubMembershipResponseDTO;
import org.example.pongrankbackend.ClubMembership.service.ClubMembershipService;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


// WARNING: actingPlayerId is sent by the client and is NOT secure authentication. Local testing only until JWT is integrated.
@RestController
@RequestMapping("/api/v1/club-memberships")
public class ClubMembershipController {

    private final ClubMembershipService clubMembershipService;

    public ClubMembershipController(ClubMembershipService clubMembershipService) {
        this.clubMembershipService = clubMembershipService;
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PostMapping
    public ResponseEntity<ClubMembershipResponseDTO> requestMembership(
            @RequestParam Long actingPlayerId,
            @Valid @RequestBody ClubMembershipRequestDTO dto) {
        ClubMembershipResponseDTO response = clubMembershipService.requestMembership(actingPlayerId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @GetMapping("/clubs/{clubId}/pending")
    public ResponseEntity<PageResponseDTO<ClubMembershipResponseDTO>> getPendingRequests(
            @PathVariable Long clubId,
            @RequestParam Long actingPlayerId,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_SIZE) int size) {
        PageResponseDTO<ClubMembershipResponseDTO> response = clubMembershipService.getPendingRequests(clubId, actingPlayerId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/clubs/{clubId}/members")
    public ResponseEntity<PageResponseDTO<ClubMembershipResponseDTO>> getActiveMembers(
            @PathVariable Long clubId,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_SIZE) int size) {
        PageResponseDTO<ClubMembershipResponseDTO> response = clubMembershipService.getActiveMembers(clubId, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/players/{playerId}")
    public ResponseEntity<PageResponseDTO<ClubMembershipResponseDTO>> getPlayerMembershipHistory(
            @PathVariable Long playerId,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_SIZE) int size) {
        PageResponseDTO<ClubMembershipResponseDTO> response = clubMembershipService.getPlayerMembershipHistory(playerId, page, size);
        return ResponseEntity.ok(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PatchMapping("/{membershipId}/approve")
    public ResponseEntity<ClubMembershipResponseDTO> approveRequest(
            @PathVariable Long membershipId,
            @RequestParam Long actingPlayerId) {
        ClubMembershipResponseDTO response = clubMembershipService.approveRequest(membershipId, actingPlayerId);
        return ResponseEntity.ok(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PatchMapping("/{membershipId}/reject")
    public ResponseEntity<ClubMembershipResponseDTO> rejectRequest(
            @PathVariable Long membershipId,
            @RequestParam Long actingPlayerId) {
        ClubMembershipResponseDTO response = clubMembershipService.rejectRequest(membershipId, actingPlayerId);
        return ResponseEntity.ok(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PatchMapping("/{membershipId}/cancel")
    public ResponseEntity<ClubMembershipResponseDTO> cancelRequest(
            @PathVariable Long membershipId,
            @RequestParam Long actingPlayerId) {
        ClubMembershipResponseDTO response = clubMembershipService.cancelRequest(membershipId, actingPlayerId);
        return ResponseEntity.ok(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PatchMapping("/{membershipId}/leave")
    public ResponseEntity<ClubMembershipResponseDTO> leaveClub(
            @PathVariable Long membershipId,
            @RequestParam Long actingPlayerId) {
        ClubMembershipResponseDTO response = clubMembershipService.leaveClub(membershipId, actingPlayerId);
        return ResponseEntity.ok(response);
    }
}

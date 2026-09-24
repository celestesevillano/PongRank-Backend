package org.example.pongrankbackend.ClubMembership.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.ClubMembership.dto.ClubMembershipRequestDTO;
import org.example.pongrankbackend.ClubMembership.dto.ClubMembershipResponseDTO;
import org.example.pongrankbackend.ClubMembership.service.ClubMembershipService;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/club-memberships")
public class ClubMembershipController {

    private final ClubMembershipService clubMembershipService;

    public ClubMembershipController(ClubMembershipService clubMembershipService) {
        this.clubMembershipService = clubMembershipService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClubMembershipResponseDTO> requestMembership(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody ClubMembershipRequestDTO dto) {
        ClubMembershipResponseDTO response = clubMembershipService.requestMembership(currentUser.getId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/clubs/{clubId}/pending")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponseDTO<ClubMembershipResponseDTO>> getPendingRequests(
            @PathVariable Long clubId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_SIZE) int size) {
        PageResponseDTO<ClubMembershipResponseDTO> response = clubMembershipService.getPendingRequests(clubId, currentUser.getId(), page, size);
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

    @PatchMapping("/{membershipId}/approve")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClubMembershipResponseDTO> approveRequest(
            @PathVariable Long membershipId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        ClubMembershipResponseDTO response = clubMembershipService.approveRequest(membershipId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{membershipId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClubMembershipResponseDTO> rejectRequest(
            @PathVariable Long membershipId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        ClubMembershipResponseDTO response = clubMembershipService.rejectRequest(membershipId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{membershipId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClubMembershipResponseDTO> cancelRequest(
            @PathVariable Long membershipId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        ClubMembershipResponseDTO response = clubMembershipService.cancelRequest(membershipId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{membershipId}/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClubMembershipResponseDTO> leaveClub(
            @PathVariable Long membershipId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        ClubMembershipResponseDTO response = clubMembershipService.leaveClub(membershipId, currentUser.getId());
        return ResponseEntity.ok(response);
    }
}

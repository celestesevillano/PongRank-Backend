package org.example.pongrankbackend.Club.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Club.dto.ClubRejectRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubReviewHistoryDTO;
import org.example.pongrankbackend.Club.dto.ClubReviewResponseDTO;
import org.example.pongrankbackend.Club.service.ClubService;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


// WARNING: Not safe until JWT is integrated. SecurityConfig should restrict /api/v1/admin/** to ROLE_SYSTEM_ADMIN.
@RestController
@RequestMapping("/api/v1/admin/clubs")
public class ClubAdminController {

    private final ClubService clubService;

    public ClubAdminController(ClubService clubService) {
        this.clubService = clubService;
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @GetMapping("/pending")
    public ResponseEntity<PageResponseDTO<ClubReviewResponseDTO>> getPendingClubs(
            @RequestParam Long actingPlayerId,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_SIZE) int size) {
        PageResponseDTO<ClubReviewResponseDTO> response = clubService.getPendingClubs(actingPlayerId, page, size);
        return ResponseEntity.ok(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PatchMapping("/{clubId}/approve")
    public ResponseEntity<ClubReviewResponseDTO> approveClub(
            @PathVariable Long clubId,
            @RequestParam Long actingPlayerId) {
        ClubReviewResponseDTO response = clubService.approveClub(clubId, actingPlayerId);
        return ResponseEntity.ok(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PatchMapping("/{clubId}/reject")
    public ResponseEntity<ClubReviewResponseDTO> rejectClub(
            @PathVariable Long clubId,
            @RequestParam Long actingPlayerId,
            @Valid @RequestBody ClubRejectRequestDTO dto) {
        ClubReviewResponseDTO response = clubService.rejectClub(clubId, actingPlayerId, dto);
        return ResponseEntity.ok(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @GetMapping("/{clubId}/reviews")
    public ResponseEntity<PageResponseDTO<ClubReviewHistoryDTO>> getReviewHistory(
            @PathVariable Long clubId,
            @RequestParam Long actingPlayerId,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_SIZE) int size) {
        PageResponseDTO<ClubReviewHistoryDTO> response = clubService.getReviewHistory(clubId, actingPlayerId, page, size);
        return ResponseEntity.ok(response);
    }
}

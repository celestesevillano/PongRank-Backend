package org.example.pongrankbackend.Club.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Club.dto.ClubRejectRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubReviewHistoryDTO;
import org.example.pongrankbackend.Club.dto.ClubReviewResponseDTO;
import org.example.pongrankbackend.Club.service.ClubService;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/clubs")
@PreAuthorize("hasRole('SYSTEM_ADMIN') or hasAuthority('ROLE_SYSTEM_ADMIN')")
public class ClubAdminController {

    private final ClubService clubService;

    public ClubAdminController(ClubService clubService) {
        this.clubService = clubService;
    }

    @GetMapping("/pending")
    public ResponseEntity<PageResponseDTO<ClubReviewResponseDTO>> getPendingClubs(
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_SIZE) int size) {
        PageResponseDTO<ClubReviewResponseDTO> response = clubService.getPendingClubs(page, size);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{clubId}/approve")
    public ResponseEntity<ClubReviewResponseDTO> approveClub(@PathVariable Long clubId) {
        ClubReviewResponseDTO response = clubService.approveClub(clubId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{clubId}/reject")
    public ResponseEntity<ClubReviewResponseDTO> rejectClub(
            @PathVariable Long clubId,
            @Valid @RequestBody ClubRejectRequestDTO dto) {
        ClubReviewResponseDTO response = clubService.rejectClub(clubId, dto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{clubId}/reviews")
    public ResponseEntity<PageResponseDTO<ClubReviewHistoryDTO>> getReviewHistory(
            @PathVariable Long clubId,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_SIZE) int size) {
        PageResponseDTO<ClubReviewHistoryDTO> response = clubService.getReviewHistory(clubId, page, size);
        return ResponseEntity.ok(response);
    }
}

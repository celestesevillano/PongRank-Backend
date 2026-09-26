package org.example.pongrankbackend.Club.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Club.dto.ClubAdminTransferRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubAffiliationDocumentRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubRegisterRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubResponseDTO;
import org.example.pongrankbackend.Club.dto.ClubResubmitRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubReviewResponseDTO;
import org.example.pongrankbackend.Club.dto.ClubUpdateRequestDTO;
import org.example.pongrankbackend.Club.service.ClubService;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clubs")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClubResponseDTO> registerClub(@Valid @RequestBody ClubRegisterRequestDTO dto) {
        ClubResponseDTO response = clubService.registerClub(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponseDTO<ClubResponseDTO>> getApprovedClubs(
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_SIZE) int size) {
        PageResponseDTO<ClubResponseDTO> response = clubService.getApprovedClubs(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{clubId}")
    public ResponseEntity<ClubResponseDTO> getClubById(@PathVariable Long clubId) {
        ClubResponseDTO response = clubService.getClubById(clubId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{clubId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClubResponseDTO> updateClub(
            @PathVariable Long clubId,
            @Valid @RequestBody ClubUpdateRequestDTO dto) {
        ClubResponseDTO response = clubService.updateClub(clubId, dto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{clubId}/review")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClubReviewResponseDTO> getClubReview(@PathVariable Long clubId) {
        ClubReviewResponseDTO response = clubService.getClubReview(clubId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{clubId}/resubmit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClubResponseDTO> resubmitClub(
            @PathVariable Long clubId,
            @Valid @RequestBody ClubResubmitRequestDTO dto) {
        ClubResponseDTO response = clubService.resubmitClub(clubId, dto);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{clubId}/affiliation-document")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClubResponseDTO> replaceAffiliationDocument(
            @PathVariable Long clubId,
            @Valid @RequestBody ClubAffiliationDocumentRequestDTO dto) {
        ClubResponseDTO response = clubService.replaceAffiliationDocument(clubId, dto);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{clubId}/admin")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClubResponseDTO> transferAdministration(
            @PathVariable Long clubId,
            @Valid @RequestBody ClubAdminTransferRequestDTO dto) {
        ClubResponseDTO response = clubService.transferAdministration(clubId, dto);
        return ResponseEntity.ok(response);
    }
}

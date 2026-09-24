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
import org.springframework.web.bind.annotation.*;


// WARNING: actingPlayerId is sent by the client and is NOT secure authentication. Local testing only until JWT is integrated.
@RestController
@RequestMapping("/api/v1/clubs")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PostMapping
    public ResponseEntity<ClubResponseDTO> registerClub(
            @RequestParam Long actingPlayerId,
            @Valid @RequestBody ClubRegisterRequestDTO dto) {
        ClubResponseDTO response = clubService.registerClub(actingPlayerId, dto);
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

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PatchMapping("/{clubId}")
    public ResponseEntity<ClubResponseDTO> updateClub(
            @PathVariable Long clubId,
            @RequestParam Long actingPlayerId,
            @Valid @RequestBody ClubUpdateRequestDTO dto) {
        ClubResponseDTO response = clubService.updateClub(clubId, actingPlayerId, dto);
        return ResponseEntity.ok(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @GetMapping("/{clubId}/review")
    public ResponseEntity<ClubReviewResponseDTO> getClubReview(
            @PathVariable Long clubId,
            @RequestParam Long actingPlayerId) {
        ClubReviewResponseDTO response = clubService.getClubReview(clubId, actingPlayerId);
        return ResponseEntity.ok(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PatchMapping("/{clubId}/resubmit")
    public ResponseEntity<ClubResponseDTO> resubmitClub(
            @PathVariable Long clubId,
            @RequestParam Long actingPlayerId,
            @Valid @RequestBody ClubResubmitRequestDTO dto) {
        ClubResponseDTO response = clubService.resubmitClub(clubId, actingPlayerId, dto);
        return ResponseEntity.ok(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PatchMapping("/{clubId}/affiliation-document")
    public ResponseEntity<ClubResponseDTO> replaceAffiliationDocument(
            @PathVariable Long clubId,
            @RequestParam Long actingPlayerId,
            @Valid @RequestBody ClubAffiliationDocumentRequestDTO dto) {
        ClubResponseDTO response = clubService.replaceAffiliationDocument(clubId, actingPlayerId, dto);
        return ResponseEntity.ok(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext (JWT)
    @PatchMapping("/{clubId}/admin")
    public ResponseEntity<ClubResponseDTO> transferAdministration(
            @PathVariable Long clubId,
            @RequestParam Long actingPlayerId,
            @Valid @RequestBody ClubAdminTransferRequestDTO dto) {
        ClubResponseDTO response = clubService.transferAdministration(clubId, actingPlayerId, dto);
        return ResponseEntity.ok(response);
    }
}

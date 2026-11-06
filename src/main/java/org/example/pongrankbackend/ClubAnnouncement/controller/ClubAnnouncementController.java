package org.example.pongrankbackend.ClubAnnouncement.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.ClubAnnouncement.dto.ClubAnnouncementRequestDTO;
import org.example.pongrankbackend.ClubAnnouncement.dto.ClubAnnouncementResponseDTO;
import org.example.pongrankbackend.ClubAnnouncement.service.ClubAnnouncementService;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clubs/{clubId}/announcements")
public class ClubAnnouncementController {

    private final ClubAnnouncementService announcementService;

    public ClubAnnouncementController(ClubAnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClubAnnouncementResponseDTO> publish(@PathVariable Long clubId,
                                                               @Valid @RequestBody ClubAnnouncementRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(announcementService.publish(clubId, dto));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponseDTO<ClubAnnouncementResponseDTO>> list(
            @PathVariable Long clubId,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequestFactory.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(announcementService.list(clubId, page, size));
    }
}

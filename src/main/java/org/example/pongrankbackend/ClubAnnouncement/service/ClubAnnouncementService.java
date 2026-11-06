package org.example.pongrankbackend.ClubAnnouncement.service;

import org.example.pongrankbackend.ClubAnnouncement.dto.ClubAnnouncementRequestDTO;
import org.example.pongrankbackend.ClubAnnouncement.dto.ClubAnnouncementResponseDTO;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;

public interface ClubAnnouncementService {

    ClubAnnouncementResponseDTO publish(Long clubId, ClubAnnouncementRequestDTO dto);

    PageResponseDTO<ClubAnnouncementResponseDTO> list(Long clubId, int page, int size);
}

package org.example.pongrankbackend.ClubAnnouncement.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.ClubAnnouncement.ClubAnnouncement;
import org.example.pongrankbackend.ClubAnnouncement.dto.ClubAnnouncementRequestDTO;
import org.example.pongrankbackend.ClubAnnouncement.dto.ClubAnnouncementResponseDTO;
import org.example.pongrankbackend.ClubAnnouncement.repository.ClubAnnouncementRepository;
import org.example.pongrankbackend.ClubMembership.ClubMembershipStatus;
import org.example.pongrankbackend.ClubMembership.repository.ClubMembershipRepository;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.example.pongrankbackend.security.SecurityUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClubAnnouncementServiceImpl implements ClubAnnouncementService {

    private final ClubAnnouncementRepository announcementRepository;
    private final ClubRepository clubRepository;
    private final ClubMembershipRepository clubMembershipRepository;

    public ClubAnnouncementServiceImpl(ClubAnnouncementRepository announcementRepository,
                                       ClubRepository clubRepository,
                                       ClubMembershipRepository clubMembershipRepository) {
        this.announcementRepository = announcementRepository;
        this.clubRepository = clubRepository;
        this.clubMembershipRepository = clubMembershipRepository;
    }

    @Override
    @Transactional
    public ClubAnnouncementResponseDTO publish(Long clubId, ClubAnnouncementRequestDTO dto) {
        Long actingPlayerId = SecurityUtils.getRequiredCurrentUserId();
        Club club = findClub(clubId);
        if (!club.getAdmin().getId().equals(actingPlayerId)) {
            throw new UnauthorizedActionException("Solo el administrador del club puede publicar avisos");
        }
        ClubAnnouncement saved = announcementRepository.save(ClubAnnouncement.builder()
                .club(club)
                .author(club.getAdmin())
                .title(dto.getTitle().trim())
                .body(dto.getBody().trim())
                .build());
        return toResponse(saved);
    }

    @Override
    public PageResponseDTO<ClubAnnouncementResponseDTO> list(Long clubId, int page, int size) {
        Long actingPlayerId = SecurityUtils.getRequiredCurrentUserId();
        Club club = findClub(clubId);
        boolean isAdmin = club.getAdmin().getId().equals(actingPlayerId);
        boolean isMember = clubMembershipRepository
                .findByPlayerIdAndClubIdAndStatus(actingPlayerId, clubId, ClubMembershipStatus.APPROVED)
                .isPresent();
        if (!isAdmin && !isMember) {
            throw new UnauthorizedActionException("Solo los miembros del club pueden ver sus avisos");
        }
        return PageResponseDTO.from(
                announcementRepository.findByClubId(clubId,
                        PageRequestFactory.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                this::toResponse);
    }

    private Club findClub(Long clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado con ID: " + clubId));
    }

    private ClubAnnouncementResponseDTO toResponse(ClubAnnouncement a) {
        return ClubAnnouncementResponseDTO.builder()
                .id(a.getId())
                .clubId(a.getClub().getId())
                .title(a.getTitle())
                .body(a.getBody())
                .authorName(a.getAuthor().getName())
                .createdAt(a.getCreatedAt())
                .build();
    }
}

package org.example.pongrankbackend.Club.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.ClubReview;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Club.dto.ClubAdminTransferRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubAffiliationDocumentRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubRegisterRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubRejectRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubResponseDTO;
import org.example.pongrankbackend.Club.dto.ClubResubmitRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubReviewHistoryDTO;
import org.example.pongrankbackend.Club.dto.ClubReviewResponseDTO;
import org.example.pongrankbackend.Club.dto.ClubUpdateRequestDTO;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.Club.repository.ClubReviewRepository;
import org.example.pongrankbackend.ClubMembership.service.ClubMembershipService;
import org.example.pongrankbackend.Membership.service.MembershipService;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.PlanRestrictionException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.example.pongrankbackend.security.SecurityUtils;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional(readOnly = true)
public class ClubServiceImpl implements ClubService {

    private final ClubRepository clubRepository;
    private final ClubReviewRepository clubReviewRepository;
    private final PlayerRepository playerRepository;
    private final ClubMembershipService clubMembershipService;
    private final MembershipService membershipService;
    private final ModelMapper modelMapper;

    public ClubServiceImpl(ClubRepository clubRepository,
                           ClubReviewRepository clubReviewRepository,
                           PlayerRepository playerRepository,
                           ClubMembershipService clubMembershipService,
                           MembershipService membershipService,
                           ModelMapper modelMapper) {
        this.clubRepository = clubRepository;
        this.clubReviewRepository = clubReviewRepository;
        this.playerRepository = playerRepository;
        this.clubMembershipService = clubMembershipService;
        this.membershipService = membershipService;
        this.modelMapper = modelMapper;
    }

    @Override
    @Transactional
    public ClubResponseDTO registerClub(ClubRegisterRequestDTO dto) {
        Long requesterId = SecurityUtils.getRequiredCurrentUserId();
        Player requester = findPlayerById(requesterId);
        validateCanAdministerClub(requesterId, null);

        if (!membershipService.canCreateClub(requesterId)) {
            throw new PlanRestrictionException("Solo los jugadores con plan ENTERPRISE pueden crear un club");
        }

        String normalizedName = dto.getName().trim();
        validateNameIsAvailable(normalizedName);

        Club club = Club.builder()
                .name(normalizedName)
                .address(dto.getAddress().trim())
                .affiliationDocumentUrl(dto.getAffiliationDocumentUrl().trim())
                .admin(requester)
                .status(ClubStatus.PENDING)
                .build();

        Club savedClub = clubRepository.save(club);
        return modelMapper.map(savedClub, ClubResponseDTO.class);
    }

    @Override
    public PageResponseDTO<ClubResponseDTO> getApprovedClubs(int page, int size) {
        return PageResponseDTO.from(
                clubRepository.findByStatus(ClubStatus.APPROVED, PageRequestFactory.of(page, size, Sort.by("name").ascending())),
                club -> modelMapper.map(club, ClubResponseDTO.class));
    }

    @Override
    public ClubResponseDTO getClubById(Long clubId) {
        Club club = findClubById(clubId);
        return modelMapper.map(club, ClubResponseDTO.class);
    }

    @Override
    @Transactional
    public ClubResponseDTO updateClub(Long clubId, ClubUpdateRequestDTO dto) {
        Long actingPlayerId = SecurityUtils.getRequiredCurrentUserId();
        Club club = findClubById(clubId);
        validateClubAdmin(club, actingPlayerId);

        if (dto.getName() != null) {
            String normalizedName = dto.getName().trim();
            if (!normalizedName.equalsIgnoreCase(club.getName())) {
                validateNameIsAvailable(normalizedName);
            }
            club.setName(normalizedName);
        }

        if (dto.getAddress() != null) {
            club.setAddress(dto.getAddress().trim());
        }

        Club updatedClub = clubRepository.saveAndFlush(club);
        return modelMapper.map(updatedClub, ClubResponseDTO.class);
    }

    @Override
    public ClubReviewResponseDTO getClubReview(Long clubId) {
        Long actingPlayerId = SecurityUtils.getRequiredCurrentUserId();
        Club club = findClubById(clubId);

        if (!isClubAdmin(club, actingPlayerId) && !isSystemAdmin(findPlayerById(actingPlayerId))) {
            throw new UnauthorizedActionException("Solo el administrador del club o el administrador general pueden ver la revisión");
        }

        return modelMapper.map(club, ClubReviewResponseDTO.class);
    }

    @Override
    @Transactional
    public ClubResponseDTO resubmitClub(Long clubId, ClubResubmitRequestDTO dto) {
        Long actingPlayerId = SecurityUtils.getRequiredCurrentUserId();
        Club club = findClubById(clubId);
        validateClubAdmin(club, actingPlayerId);
        validateClubStatus(club, ClubStatus.REJECTED, "Solo un club rechazado puede reenviarse a revisión");
        validateCanAdministerClub(actingPlayerId, clubId);

        return sendToReview(club, dto.getAffiliationDocumentUrl());
    }

    @Override
    @Transactional
    public ClubResponseDTO replaceAffiliationDocument(Long clubId, ClubAffiliationDocumentRequestDTO dto) {
        Long actingPlayerId = SecurityUtils.getRequiredCurrentUserId();
        Club club = findClubById(clubId);
        validateClubAdmin(club, actingPlayerId);

        if (club.getStatus() == ClubStatus.REJECTED) {
            throw new ConflictException("Un club rechazado debe reemplazar su documento al reenviarse a revisión");
        }

        return sendToReview(club, dto.getAffiliationDocumentUrl());
    }

    @Override
    @Transactional
    public ClubResponseDTO transferAdministration(Long clubId, ClubAdminTransferRequestDTO dto) {
        Long actingPlayerId = SecurityUtils.getRequiredCurrentUserId();
        Club club = findClubById(clubId);
        validateClubAdmin(club, actingPlayerId);
        validateClubStatus(club, ClubStatus.APPROVED, "Solo un club aprobado puede transferir su administración");

        if (actingPlayerId.equals(dto.getNewAdminPlayerId())) {
            throw new ConflictException("El nuevo administrador debe ser un jugador distinto al actual");
        }

        if (clubRepository.existsByAdminIdAndStatusInAndIdNot(dto.getNewAdminPlayerId(), ClubStatus.ACTIVE_STATUSES, clubId)) {
            throw new ConflictException("El nuevo administrador ya administra otro club en revisión o aprobado");
        }

        Player newAdmin = clubMembershipService.transferAdminRole(club, dto.getNewAdminPlayerId());
        club.setAdmin(newAdmin);

        Club updatedClub = clubRepository.saveAndFlush(club);
        return modelMapper.map(updatedClub, ClubResponseDTO.class);
    }

    @Override
    public PageResponseDTO<ClubReviewResponseDTO> getPendingClubs(int page, int size) {
        findSystemAdmin(SecurityUtils.getRequiredCurrentUserId());

        // Oldest requests first, so they are reviewed in arrival order
        return PageResponseDTO.from(
                clubRepository.findByStatus(ClubStatus.PENDING, PageRequestFactory.of(page, size, Sort.by("updatedAt").ascending())),
                club -> modelMapper.map(club, ClubReviewResponseDTO.class));
    }

    @Override
    @Transactional
    public ClubReviewResponseDTO approveClub(Long clubId) {
        Player reviewer = findSystemAdmin(SecurityUtils.getRequiredCurrentUserId());
        Club club = findClubById(clubId);
        validateClubStatus(club, ClubStatus.PENDING, "El club ya fue revisado o no está en estado PENDING");
        validateCanAdministerClub(club.getAdmin().getId(), clubId);

        club.setStatus(ClubStatus.APPROVED);
        club.setRejectionReason(null);
        Club approvedClub = clubRepository.saveAndFlush(club);

        recordReview(club, reviewer, ClubStatus.APPROVED, null);
        clubMembershipService.addAdminAsMember(club);

        return modelMapper.map(approvedClub, ClubReviewResponseDTO.class);
    }

    @Override
    @Transactional
    public ClubReviewResponseDTO rejectClub(Long clubId, ClubRejectRequestDTO dto) {
        Player reviewer = findSystemAdmin(SecurityUtils.getRequiredCurrentUserId());
        Club club = findClubById(clubId);
        validateClubStatus(club, ClubStatus.PENDING, "El club ya fue revisado o no está en estado PENDING");

        String reason = dto.getRejectionReason().trim();
        club.setStatus(ClubStatus.REJECTED);
        club.setRejectionReason(reason);
        Club rejectedClub = clubRepository.saveAndFlush(club);

        recordReview(club, reviewer, ClubStatus.REJECTED, reason);

        return modelMapper.map(rejectedClub, ClubReviewResponseDTO.class);
    }

    @Override
    public PageResponseDTO<ClubReviewHistoryDTO> getReviewHistory(Long clubId, int page, int size) {
        findSystemAdmin(SecurityUtils.getRequiredCurrentUserId());
        findClubById(clubId);

        return PageResponseDTO.from(
                clubReviewRepository.findByClubId(clubId, PageRequestFactory.of(page, size, Sort.by("reviewedAt").descending())),
                review -> modelMapper.map(review, ClubReviewHistoryDTO.class));
    }

    private ClubResponseDTO sendToReview(Club club, String newAffiliationDocumentUrl) {
        if (newAffiliationDocumentUrl != null) {
            club.setAffiliationDocumentUrl(newAffiliationDocumentUrl.trim());
        }
        club.setStatus(ClubStatus.PENDING);
        club.setRejectionReason(null);

        Club pendingClub = clubRepository.saveAndFlush(club);
        return modelMapper.map(pendingClub, ClubResponseDTO.class);
    }

    private void recordReview(Club club, Player reviewer, ClubStatus result, String reason) {
        ClubReview review = ClubReview.builder()
                .club(club)
                .reviewer(reviewer)
                .result(result)
                .reason(reason)
                .affiliationDocumentUrl(club.getAffiliationDocumentUrl())
                .build();

        clubReviewRepository.save(review);
    }

    // Single-club rule (D1): a player can only be responsible for one club in progress at a time.
    // currentClubId excludes the club being resubmitted or approved; null when registering a new club.
    private void validateCanAdministerClub(Long playerId, Long currentClubId) {
        if (clubMembershipService.hasActiveMembershipOutsideClub(playerId, currentClubId)) {
            throw new ConflictException("El jugador ya pertenece a otro club o tiene una solicitud de ingreso pendiente");
        }

        boolean administersAnotherClub = currentClubId == null
                ? clubRepository.existsByAdminIdAndStatusIn(playerId, ClubStatus.ACTIVE_STATUSES)
                : clubRepository.existsByAdminIdAndStatusInAndIdNot(playerId, ClubStatus.ACTIVE_STATUSES, currentClubId);

        if (administersAnotherClub) {
            throw new ConflictException("El jugador ya administra otro club en revisión o aprobado");
        }
    }

    private Player findPlayerById(Long playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId));    }

    private Club findClubById(Long clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado con ID: " + clubId));    }

    private void validateClubStatus(Club club, ClubStatus expectedStatus, String message) {
        if (club.getStatus() != expectedStatus) {
            throw new ConflictException(message);
        }
    }

    private void validateNameIsAvailable(String normalizedName) {
        if (clubRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new ConflictException("Ya existe un club registrado con el nombre '" + normalizedName + "'");
        }
    }

    private boolean isClubAdmin(Club club, Long playerId) {
        return club.getAdmin().getId().equals(playerId);
    }

    private void validateClubAdmin(Club club, Long actingPlayerId) {
        if (!isClubAdmin(club, actingPlayerId)) {
            throw new UnauthorizedActionException("Solo el administrador del club puede realizar esta acción");
        }
    }

    private boolean isSystemAdmin(Player player) {
        return player.getRole() == Role.ROLE_SYSTEM_ADMIN;
    }

    private Player findSystemAdmin(Long actingPlayerId) {
        Player actingPlayer = findPlayerById(actingPlayerId);

        if (!isSystemAdmin(actingPlayer)) {
            throw new UnauthorizedActionException("Solo el administrador general de PongRank puede revisar clubes");
        }

        return actingPlayer;
    }
}

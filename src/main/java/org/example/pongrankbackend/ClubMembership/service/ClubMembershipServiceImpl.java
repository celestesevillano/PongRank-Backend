package org.example.pongrankbackend.ClubMembership.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.ClubMembership.ClubMembership;
import org.example.pongrankbackend.ClubMembership.ClubMembershipRole;
import org.example.pongrankbackend.ClubMembership.ClubMembershipStatus;
import org.example.pongrankbackend.ClubMembership.dto.ClubMembershipRequestDTO;
import org.example.pongrankbackend.ClubMembership.dto.ClubMembershipResponseDTO;
import org.example.pongrankbackend.ClubMembership.repository.ClubMembershipRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.example.pongrankbackend.Membership.service.MembershipService;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.PlanRestrictionException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.example.pongrankbackend.security.SecurityUtils;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ClubMembershipServiceImpl implements ClubMembershipService {

    private final ClubMembershipRepository clubMembershipRepository;
    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final MembershipService membershipService;
    private final ModelMapper modelMapper;

    public ClubMembershipServiceImpl(ClubMembershipRepository clubMembershipRepository,
                                     ClubRepository clubRepository,
                                     PlayerRepository playerRepository,
                                     MembershipService membershipService,
                                     ModelMapper modelMapper) {
        this.clubMembershipRepository = clubMembershipRepository;
        this.clubRepository = clubRepository;
        this.playerRepository = playerRepository;
        this.membershipService = membershipService;
        this.modelMapper = modelMapper;
    }

    @Override
    @Transactional
    public ClubMembershipResponseDTO requestMembership(ClubMembershipRequestDTO dto) {
        Long actingPlayerId = SecurityUtils.getRequiredCurrentUserId();
        Player player = playerRepository.findById(actingPlayerId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + actingPlayerId));
        Club club = findClubById(dto.getClubId());
        validateClubIsApproved(club);

        if (!membershipService.isPaidMember(actingPlayerId)) {
            throw new PlanRestrictionException("El plan FREEMIUM no puede unirse a un club; actualiza tu plan a BASIC o superior");
        }

        if (clubMembershipRepository.existsByPlayerIdAndStatusIn(actingPlayerId, ClubMembershipStatus.ACTIVE_STATUSES)) {
            throw new ConflictException("El jugador ya pertenece a un club o tiene una solicitud pendiente");
        }

        if (clubRepository.existsByAdminIdAndStatusIn(actingPlayerId, ClubStatus.ACTIVE_STATUSES)) {
            throw new ConflictException("El jugador administra un club en revisión o aprobado y no puede unirse a otro");
        }

        ClubMembership membership = ClubMembership.builder()
                .player(player)
                .club(club)
                .status(ClubMembershipStatus.PENDING)
                .role(ClubMembershipRole.MEMBER)
                .build();

        ClubMembership savedMembership = clubMembershipRepository.save(membership);
        return toResponse(savedMembership);
    }

    @Override
    public PageResponseDTO<ClubMembershipResponseDTO> getPendingRequests(Long clubId, int page, int size) {
        Club club = findClubById(clubId);
        validateClubAdmin(club, SecurityUtils.getRequiredCurrentUserId());

        return PageResponseDTO.from(clubMembershipRepository.findByClubIdAndStatus(
                clubId, ClubMembershipStatus.PENDING, PageRequestFactory.of(page, size, Sort.by("createdAt").ascending())),
                this::toResponse);
    }

    @Override
    @Transactional
    public ClubMembershipResponseDTO approveRequest(Long membershipId) {
        ClubMembership membership = findPendingMembershipManagedBy(membershipId, SecurityUtils.getRequiredCurrentUserId());
        validateClubIsApproved(membership.getClub());

        Long playerId = membership.getPlayer().getId();
        if (clubMembershipRepository.existsByPlayerIdAndStatusInAndClubIdNot(
                playerId, List.of(ClubMembershipStatus.APPROVED), membership.getClub().getId())) {
            throw new ConflictException("El jugador ya pertenece a otro club");
        }

        membership.setStatus(ClubMembershipStatus.APPROVED);
        membership.setJoinedAt(LocalDateTime.now());

        return toResponse(clubMembershipRepository.saveAndFlush(membership));
    }

    @Override
    @Transactional
    public ClubMembershipResponseDTO rejectRequest(Long membershipId) {
        ClubMembership membership = findPendingMembershipManagedBy(membershipId, SecurityUtils.getRequiredCurrentUserId());
        membership.setStatus(ClubMembershipStatus.REJECTED);

        return toResponse(clubMembershipRepository.saveAndFlush(membership));
    }

    @Override
    @Transactional
    public ClubMembershipResponseDTO cancelRequest(Long membershipId) {
        Long actingPlayerId = SecurityUtils.getRequiredCurrentUserId();
        ClubMembership membership = findMembershipById(membershipId);
        validateMembershipOwner(membership, actingPlayerId);
        validateMembershipStatus(membership, ClubMembershipStatus.PENDING);

        membership.setStatus(ClubMembershipStatus.CANCELLED);

        return toResponse(clubMembershipRepository.saveAndFlush(membership));
    }

    @Override
    @Transactional
    public ClubMembershipResponseDTO leaveClub(Long membershipId) {
        Long actingPlayerId = SecurityUtils.getRequiredCurrentUserId();
        ClubMembership membership = findMembershipById(membershipId);
        validateMembershipOwner(membership, actingPlayerId);
        validateMembershipStatus(membership, ClubMembershipStatus.APPROVED);

        if (isClubAdmin(membership.getClub(), actingPlayerId)) {
            throw new ConflictException("El administrador debe transferir la administración antes de abandonar el club");
        }

        membership.setStatus(ClubMembershipStatus.LEFT);
        membership.setLeftAt(LocalDateTime.now());

        return toResponse(clubMembershipRepository.saveAndFlush(membership));
    }

    @Override
    public PageResponseDTO<ClubMembershipResponseDTO> getActiveMembers(Long clubId, int page, int size) {
        findClubById(clubId);
        return PageResponseDTO.from(clubMembershipRepository.findByClubIdAndStatus(
                clubId, ClubMembershipStatus.APPROVED, PageRequestFactory.of(page, size, Sort.by("joinedAt").ascending())),
                this::toResponse);
    }

    @Override
    public PageResponseDTO<ClubMembershipResponseDTO> getPlayerMembershipHistory(Long playerId, int page, int size) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }
        return PageResponseDTO.from(clubMembershipRepository.findByPlayerId(
                playerId, PageRequestFactory.of(page, size, Sort.by("createdAt").descending())),
                this::toResponse);
    }

    @Override
    public boolean hasActiveMembershipOutsideClub(Long playerId, Long clubId) {
        if (clubId == null) {
            return clubMembershipRepository.existsByPlayerIdAndStatusIn(playerId, ClubMembershipStatus.ACTIVE_STATUSES);
        }
        return clubMembershipRepository.existsByPlayerIdAndStatusInAndClubIdNot(
                playerId, ClubMembershipStatus.ACTIVE_STATUSES, clubId);
    }

    @Override
    public boolean isActiveMember(Long clubId, Long playerId) {
        return clubMembershipRepository
                .findByPlayerIdAndClubIdAndStatus(playerId, clubId, ClubMembershipStatus.APPROVED)
                .isPresent();
    }

    // Joins the caller's transaction: if this fails, the club approval is rolled back too
    @Override
    @Transactional
    public void addAdminAsMember(Club club) {
        Player admin = club.getAdmin();

        boolean alreadyMember = clubMembershipRepository
                .findByPlayerIdAndClubIdAndStatus(admin.getId(), club.getId(), ClubMembershipStatus.APPROVED)
                .isPresent();
        if (alreadyMember) {
            return;
        }

        if (hasActiveMembershipOutsideClub(admin.getId(), club.getId())) {
            throw new ConflictException("El administrador del club ya pertenece a otro club o tiene una solicitud pendiente");
        }

        ClubMembership adminMembership = ClubMembership.builder()
                .player(admin)
                .club(club)
                .status(ClubMembershipStatus.APPROVED)
                .role(ClubMembershipRole.CLUB_ADMIN)
                .joinedAt(LocalDateTime.now())
                .build();

        clubMembershipRepository.save(adminMembership);
    }

    // Joins the caller's transaction: Club.admin and both roles change together or not at all
    @Override
    @Transactional
    public Player transferAdminRole(Club club, Long newAdminPlayerId) {
        Long clubId = club.getId();

        ClubMembership currentAdminMembership = clubMembershipRepository
                .findByPlayerIdAndClubIdAndStatus(club.getAdmin().getId(), clubId, ClubMembershipStatus.APPROVED)
                .orElseThrow(() -> new ConflictException("El administrador actual no tiene una membresía activa en el club"));
        ClubMembership newAdminMembership = clubMembershipRepository
                .findByPlayerIdAndClubIdAndStatus(newAdminPlayerId, clubId, ClubMembershipStatus.APPROVED)
                .orElseThrow(() -> new ConflictException("El nuevo administrador debe ser miembro activo del club"));
        currentAdminMembership.setRole(ClubMembershipRole.MEMBER);
        newAdminMembership.setRole(ClubMembershipRole.CLUB_ADMIN);

        clubMembershipRepository.save(currentAdminMembership);
        clubMembershipRepository.save(newAdminMembership);

        return newAdminMembership.getPlayer();
    }

    private Club findClubById(Long clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado con ID: " + clubId));    }

    private ClubMembership findMembershipById(Long membershipId) {
        return clubMembershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membresía no encontrada con ID: " + membershipId));    }

    private ClubMembership findPendingMembershipManagedBy(Long membershipId, Long actingPlayerId) {
        ClubMembership membership = findMembershipById(membershipId);
        validateClubAdmin(membership.getClub(), actingPlayerId);
        validateMembershipStatus(membership, ClubMembershipStatus.PENDING);
        return membership;
    }

    private boolean isClubAdmin(Club club, Long playerId) {
        return club.getAdmin().getId().equals(playerId);
    }

    private void validateClubAdmin(Club club, Long actingPlayerId) {
        if (!isClubAdmin(club, actingPlayerId)) {
            throw new UnauthorizedActionException("Solo el administrador del club puede gestionar sus membresías");
        }
    }

    private void validateMembershipOwner(ClubMembership membership, Long actingPlayerId) {
        if (!membership.getPlayer().getId().equals(actingPlayerId)) {
            throw new UnauthorizedActionException("Solo el propio jugador puede realizar esta acción sobre su membresía");
        }
    }

    private void validateMembershipStatus(ClubMembership membership, ClubMembershipStatus expectedStatus) {
        if (membership.getStatus() != expectedStatus) {
            throw new ConflictException("La membresía debe estar en estado " + expectedStatus + " para realizar esta acción");
        }
    }

    private void validateClubIsApproved(Club club) {
        if (club.getStatus() != ClubStatus.APPROVED) {
            throw new ConflictException("El club no está aprobado y no puede aceptar miembros");
        }
    }

    private ClubMembershipResponseDTO toResponse(ClubMembership membership) {
        return modelMapper.map(membership, ClubMembershipResponseDTO.class);
    }

}

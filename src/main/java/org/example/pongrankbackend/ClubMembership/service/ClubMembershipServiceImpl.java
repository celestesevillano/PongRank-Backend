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
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class ClubMembershipServiceImpl implements ClubMembershipService {

    private final ClubMembershipRepository clubMembershipRepository;
    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final ModelMapper modelMapper;

    public ClubMembershipServiceImpl(ClubMembershipRepository clubMembershipRepository,
                                     ClubRepository clubRepository,
                                     PlayerRepository playerRepository,
                                     ModelMapper modelMapper) {
        this.clubMembershipRepository = clubMembershipRepository;
        this.clubRepository = clubRepository;
        this.playerRepository = playerRepository;
        this.modelMapper = modelMapper;
    }

    // TODO: Replace actingPlayerId with the authenticated player obtained from the JWT (SecurityContext)
    @Override
    @Transactional
    public ClubMembershipResponseDTO requestMembership(Long actingPlayerId, ClubMembershipRequestDTO dto) {
        Player player = playerRepository.findById(actingPlayerId)
                .orElseThrow(() -> new NoSuchElementException("Jugador no encontrado con ID: " + actingPlayerId)); // TODO: Replace with custom ResourceNotFoundException

        Club club = findClubById(dto.getClubId());
        validateClubIsApproved(club);

        if (clubMembershipRepository.existsByPlayerIdAndStatusIn(actingPlayerId, ClubMembershipStatus.ACTIVE_STATUSES)) {
            // TODO: Replace with custom ConflictException
            throw new IllegalStateException("El jugador ya pertenece a un club o tiene una solicitud pendiente");
        }

        if (clubRepository.existsByAdminIdAndStatusIn(actingPlayerId, ClubStatus.ACTIVE_STATUSES)) {
            // TODO: Replace with custom ConflictException
            throw new IllegalStateException("El jugador administra un club en revisión o aprobado y no puede unirse a otro");
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

    // TODO: Replace actingPlayerId with the authenticated player obtained from the JWT (SecurityContext)
    @Override
    public PageResponseDTO<ClubMembershipResponseDTO> getPendingRequests(Long clubId, Long actingPlayerId, int page, int size) {
        Club club = findClubById(clubId);
        validateClubAdmin(club, actingPlayerId);

        return PageResponseDTO.from(clubMembershipRepository.findByClubIdAndStatus(
                clubId, ClubMembershipStatus.PENDING, PageRequestFactory.of(page, size, Sort.by("createdAt").ascending())),
                this::toResponse);
    }

    // TODO: Replace actingPlayerId with the authenticated player obtained from the JWT (SecurityContext)
    @Override
    @Transactional
    public ClubMembershipResponseDTO approveRequest(Long membershipId, Long actingPlayerId) {
        ClubMembership membership = findPendingMembershipManagedBy(membershipId, actingPlayerId);
        validateClubIsApproved(membership.getClub());

        Long playerId = membership.getPlayer().getId();
        if (clubMembershipRepository.existsByPlayerIdAndStatusInAndClubIdNot(
                playerId, List.of(ClubMembershipStatus.APPROVED), membership.getClub().getId())) {
            // TODO: Replace with custom ConflictException
            throw new IllegalStateException("El jugador ya pertenece a otro club");
        }

        membership.setStatus(ClubMembershipStatus.APPROVED);
        membership.setJoinedAt(LocalDateTime.now());

        return toResponse(clubMembershipRepository.saveAndFlush(membership));
    }

    // TODO: Replace actingPlayerId with the authenticated player obtained from the JWT (SecurityContext)
    @Override
    @Transactional
    public ClubMembershipResponseDTO rejectRequest(Long membershipId, Long actingPlayerId) {
        ClubMembership membership = findPendingMembershipManagedBy(membershipId, actingPlayerId);
        membership.setStatus(ClubMembershipStatus.REJECTED);

        return toResponse(clubMembershipRepository.saveAndFlush(membership));
    }

    // TODO: Replace actingPlayerId with the authenticated player obtained from the JWT (SecurityContext)
    @Override
    @Transactional
    public ClubMembershipResponseDTO cancelRequest(Long membershipId, Long actingPlayerId) {
        ClubMembership membership = findMembershipById(membershipId);
        validateMembershipOwner(membership, actingPlayerId);
        validateMembershipStatus(membership, ClubMembershipStatus.PENDING);

        membership.setStatus(ClubMembershipStatus.CANCELLED);

        return toResponse(clubMembershipRepository.saveAndFlush(membership));
    }

    // TODO: Replace actingPlayerId with the authenticated player obtained from the JWT (SecurityContext)
    @Override
    @Transactional
    public ClubMembershipResponseDTO leaveClub(Long membershipId, Long actingPlayerId) {
        ClubMembership membership = findMembershipById(membershipId);
        validateMembershipOwner(membership, actingPlayerId);
        validateMembershipStatus(membership, ClubMembershipStatus.APPROVED);

        if (isClubAdmin(membership.getClub(), actingPlayerId)) {
            // TODO: Replace with custom ConflictException
            throw new IllegalStateException("El administrador debe transferir la administración antes de abandonar el club");
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
            // TODO: Replace with custom ResourceNotFoundException
            throw new NoSuchElementException("Jugador no encontrado con ID: " + playerId);
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
            // TODO: Replace with custom ConflictException
            throw new IllegalStateException("El administrador del club ya pertenece a otro club o tiene una solicitud pendiente");
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
                .orElseThrow(() -> new IllegalStateException("El administrador actual no tiene una membresía activa en el club")); // TODO: Replace with custom ConflictException

        ClubMembership newAdminMembership = clubMembershipRepository
                .findByPlayerIdAndClubIdAndStatus(newAdminPlayerId, clubId, ClubMembershipStatus.APPROVED)
                .orElseThrow(() -> new IllegalStateException("El nuevo administrador debe ser miembro activo del club")); // TODO: Replace with custom ConflictException

        currentAdminMembership.setRole(ClubMembershipRole.MEMBER);
        newAdminMembership.setRole(ClubMembershipRole.CLUB_ADMIN);

        clubMembershipRepository.save(currentAdminMembership);
        clubMembershipRepository.save(newAdminMembership);

        return newAdminMembership.getPlayer();
    }

    private Club findClubById(Long clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new NoSuchElementException("Club no encontrado con ID: " + clubId)); // TODO: Replace with custom ResourceNotFoundException
    }

    private ClubMembership findMembershipById(Long membershipId) {
        return clubMembershipRepository.findById(membershipId)
                .orElseThrow(() -> new NoSuchElementException("Membresía no encontrada con ID: " + membershipId)); // TODO: Replace with custom ResourceNotFoundException
    }

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
            // TODO: Replace with custom ForbiddenException / AccessDeniedException
            throw new IllegalArgumentException("Solo el administrador del club puede gestionar sus membresías");
        }
    }

    private void validateMembershipOwner(ClubMembership membership, Long actingPlayerId) {
        if (!membership.getPlayer().getId().equals(actingPlayerId)) {
            // TODO: Replace with custom ForbiddenException / AccessDeniedException
            throw new IllegalArgumentException("Solo el propio jugador puede realizar esta acción sobre su membresía");
        }
    }

    private void validateMembershipStatus(ClubMembership membership, ClubMembershipStatus expectedStatus) {
        if (membership.getStatus() != expectedStatus) {
            // TODO: Replace with custom ConflictException
            throw new IllegalStateException("La membresía debe estar en estado " + expectedStatus + " para realizar esta acción");
        }
    }

    private void validateClubIsApproved(Club club) {
        if (club.getStatus() != ClubStatus.APPROVED) {
            // TODO: Replace with custom ConflictException
            throw new IllegalStateException("El club no está aprobado y no puede aceptar miembros");
        }
    }

    private ClubMembershipResponseDTO toResponse(ClubMembership membership) {
        return modelMapper.map(membership, ClubMembershipResponseDTO.class);
    }

}

package org.example.pongrankbackend.Player.service;

import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.ClubMembership.ClubMembership;
import org.example.pongrankbackend.ClubMembership.ClubMembershipStatus;
import org.example.pongrankbackend.ClubMembership.repository.ClubMembershipRepository;
import org.example.pongrankbackend.CommunityMembership.CommunityMembership;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.CommunityMembership.MembershipStatus;
import org.example.pongrankbackend.CommunityMembership.repository.CommunityMembershipRepository;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.service.MembershipService;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.dto.DeleteAccountRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.dto.PlayerUpdateRequestDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.auth.dto.AuthResponseDTO;
import org.example.pongrankbackend.auth.service.AuthService;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.InvalidCredentialsException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.email.service.EmailService;
import org.example.pongrankbackend.security.SecurityUtils;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PlayerServiceImpl implements PlayerService {

    private final PlayerRepository playerRepository;
    private final ClubRepository clubRepository;
    private final ClubMembershipRepository clubMembershipRepository;
    private final CommunityMembershipRepository communityMembershipRepository;
    private final MembershipService membershipService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ModelMapper modelMapper;
    private final AuthService authService;

    public PlayerServiceImpl(PlayerRepository playerRepository,
                             ClubRepository clubRepository,
                             ClubMembershipRepository clubMembershipRepository,
                             CommunityMembershipRepository communityMembershipRepository,
                             MembershipService membershipService,
                             PasswordEncoder passwordEncoder,
                             EmailService emailService,
                             ModelMapper modelMapper,
                             AuthService authService) {
        this.playerRepository = playerRepository;
        this.clubRepository = clubRepository;
        this.clubMembershipRepository = clubMembershipRepository;
        this.communityMembershipRepository = communityMembershipRepository;
        this.membershipService = membershipService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.modelMapper = modelMapper;
        this.authService = authService;
    }

    @Override
    @Transactional
    public PlayerResponseDTO registerPlayer(PlayerRegisterRequestDTO dto) {
        AuthResponseDTO authResponse = authService.register(dto);
        return authResponse.getPlayer();
    }

    @Override
    public PlayerResponseDTO getPlayerById(Long id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + id));
        PlayerResponseDTO response = modelMapper.map(player, PlayerResponseDTO.class);
        Long currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        boolean isOwnerOrAdmin = (currentUserId != null && currentUserId.equals(id))
                || SecurityUtils.hasRole("SYSTEM_ADMIN");
        if (!isOwnerOrAdmin && Boolean.FALSE.equals(player.getShareContact())) {
            response.setEmail(null);
            response.setWhatsapp(null);
        }
        return response;
    }

    @Override
    public PlayerSummaryDTO getPlayerSummaryById(Long id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + id));
        return modelMapper.map(player, PlayerSummaryDTO.class);
    }

    @Override
    @Transactional
    public PlayerResponseDTO updatePlayer(Long playerId, PlayerUpdateRequestDTO dto) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId));

        modelMapper.map(dto, player);

        Player updatedPlayer = playerRepository.save(player);
        return modelMapper.map(updatedPlayer, PlayerResponseDTO.class);
    }

    @Override
    @Transactional
    public void deleteAccount(DeleteAccountRequestDTO dto) {
        Long playerId = SecurityUtils.getRequiredCurrentUserId();
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId));

        if (!passwordEncoder.matches(dto.getPassword(), player.getPassword())) {
            throw new InvalidCredentialsException("La contraseña ingresada no es correcta");
        }

        if (clubRepository.existsByAdminIdAndStatusIn(playerId, ClubStatus.ACTIVE_STATUSES)) {
            throw new ConflictException(
                    "Debes transferir la administración de tu club antes de eliminar tu cuenta");
        }

        List<CommunityMembership> activeCommunityMemberships =
                communityMembershipRepository.findByPlayerIdWithCommunity(playerId, MembershipStatus.ACTIVE);

        for (CommunityMembership membership : activeCommunityMemberships) {
            boolean isAdmin = membership.getRole() == CommunityRole.COMMUNITY_ADMIN;
            if (!isAdmin) {
                continue;
            }

            long activeAdmins = communityMembershipRepository.countByCommunityIdAndRoleAndStatus(
                    membership.getCommunity().getId(), CommunityRole.COMMUNITY_ADMIN, MembershipStatus.ACTIVE);

            if (activeAdmins <= 1) {
                throw new ConflictException("Debes promover a otro administrador en la comunidad '"
                        + membership.getCommunity().getName() + "' antes de eliminar tu cuenta");
            }
        }

        MembershipPlan activePlan = membershipService.getActivePlan(playerId);
        emailService.sendAccountDeletedEmail(player, activePlan);

        LocalDateTime now = LocalDateTime.now();

        for (CommunityMembership membership : activeCommunityMemberships) {
            membership.setStatus(MembershipStatus.INACTIVE);
            membership.setLeftAt(now);
        }
        communityMembershipRepository.saveAll(activeCommunityMemberships);

        List<ClubMembership> activeClubMemberships = clubMembershipRepository
                .findByPlayerId(playerId, org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .filter(m -> m.getStatus() == ClubMembershipStatus.APPROVED || m.getStatus() == ClubMembershipStatus.PENDING)
                .toList();
        for (ClubMembership membership : activeClubMemberships) {
            membership.setStatus(ClubMembershipStatus.LEFT);
            membership.setLeftAt(now);
        }
        clubMembershipRepository.saveAll(activeClubMemberships);

        player.setEmail(player.getEmail() + ".deleted." + playerId + "." + now.toEpochSecond(java.time.ZoneOffset.UTC));
        player.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        player.setStatus(PlayerStatus.DELETED);
        playerRepository.save(player);
    }
}

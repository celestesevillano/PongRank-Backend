package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.Community.CommunityType;
import org.example.pongrankbackend.Community.dto.CommunityCreateRequestDTO;
import org.example.pongrankbackend.Community.dto.CommunityAdministrationCandidateDTO;
import org.example.pongrankbackend.Community.dto.CommunityDetailResponseDTO;
import org.example.pongrankbackend.Community.dto.CommunityResponseDTO;
import org.example.pongrankbackend.Community.dto.CommunityUpdateRequestDTO;
import org.example.pongrankbackend.Community.repository.CommunityRepository;
import org.example.pongrankbackend.CommunityMembership.CommunityMembership;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.CommunityMembership.MembershipStatus;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberAddRequestDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberResponseDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberRoleUpdateDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityRankingEntryDTO;
import org.example.pongrankbackend.CommunityMembership.repository.CommunityMembershipRepository;
import org.example.pongrankbackend.Membership.Membership;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.InvalidCommunityException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CommunityServiceImpl implements CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository membershipRepository;
    private final PlayerRepository playerRepository;
    private final CommunityMapper communityMapper;
    private final CommunityAccessPolicy accessPolicy;
    private final CommunityPlanLimits planLimits;
    private final CommunityMemberService memberService;
    private final CommunityLifecycle lifecycle;

    public CommunityServiceImpl(CommunityRepository communityRepository,
                                CommunityMembershipRepository membershipRepository,
                                PlayerRepository playerRepository,
                                CommunityMapper communityMapper,
                                CommunityAccessPolicy accessPolicy,
                                CommunityPlanLimits planLimits,
                                CommunityMemberService memberService,
                                CommunityLifecycle lifecycle) {
        this.communityRepository = communityRepository;
        this.membershipRepository = membershipRepository;
        this.playerRepository = playerRepository;
        this.communityMapper = communityMapper;
        this.accessPolicy = accessPolicy;
        this.planLimits = planLimits;
        this.memberService = memberService;
        this.lifecycle = lifecycle;
    }

    @Override
    @Transactional
    public CommunityResponseDTO createCommunity(CommunityCreateRequestDTO dto, Long requesterId) {
        String name = dto.getName().trim();

        if (communityRepository.existsByNameIgnoreCaseAndStatus(name, CommunityStatus.ACTIVE)) {
            throw new ConflictException("Community name already in use: " + name);
        }

        Player creator = loadPlayer(requesterId);
        String customLabel = resolveCustomLabel(dto.getCommunityType(), dto.getCustomLabel());
        planLimits.verifyTotalMembershipLimit(requesterId);
        planLimits.verifyCreationLimit(requesterId);
        Community community = buildCommunity(dto, name, creator, customLabel);
        lifecycle.reconcile(community, LocalDateTime.now());

        try {
            Community saved = communityRepository.save(community);
            createAdminMembership(saved, creator);
            return communityMapper.toCommunityDto(saved, CommunityRole.COMMUNITY_ADMIN, 1L);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Community name already in use: " + name);
        }
    }

    @Override
    public Page<CommunityResponseDTO> searchCommunities(String name, CommunityType type,
                                                        Pageable pageable, Long requesterId) {
        String nameFilter = (name == null) ? "" : name.trim();

        Page<Community> page = communityRepository.searchCommunities(
                CommunityStatus.ACTIVE, nameFilter, type, pageable);

        List<Long> ids = page.getContent().stream().map(Community::getId).toList();
        Map<Long, Long> counts = countMembersByCommunity(ids);

        return page.map(community -> communityMapper.toCommunityDto(
                community,
                findRequesterRole(community.getId(), requesterId),
                counts.getOrDefault(community.getId(), 0L)));
    }

    @Override
    public List<CommunityResponseDTO> getMyCommunities(Long requesterId) {
        List<CommunityMembership> memberships = membershipRepository
                .findByPlayerIdWithActiveCommunity(requesterId, MembershipStatus.ACTIVE, CommunityStatus.ACTIVE);

        return mapMemberships(memberships);
    }

    @Override
    public List<CommunityResponseDTO> getMyCommunitiesIncludingClosed(Long requesterId) {
        return mapMemberships(membershipRepository.findByPlayerIdWithCommunity(requesterId, MembershipStatus.ACTIVE));
    }

    private List<CommunityResponseDTO> mapMemberships(List<CommunityMembership> memberships) {
        List<Long> ids = memberships.stream()
                .map(m -> m.getCommunity().getId())
                .toList();
        Map<Long, Long> counts = countMembersByCommunity(ids);

        return memberships.stream()
                .map(m -> communityMapper.toCommunityDto(
                        m.getCommunity(),
                        m.getRole(),
                        counts.getOrDefault(m.getCommunity().getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public CommunityDetailResponseDTO getCommunityById(Long communityId, Long requesterId) {
        Community community = loadCommunityForUpdate(communityId);
        LocalDateTime now = LocalDateTime.now();
        lifecycle.reconcile(community, now);
        CommunityRole myRole = findRequesterRole(communityId, requesterId);
        if (community.getStatus() != CommunityStatus.ACTIVE && myRole == null
                && !community.managingPlayerId().equals(requesterId)) {
            throw new ResourceNotFoundException("No encontramos la comunidad");
        }
        long memberCount = membershipRepository
                .countByCommunityIdAndStatus(communityId, MembershipStatus.ACTIVE);

        long adminCount = membershipRepository.countByCommunityIdAndRoleAndStatus(
                communityId, CommunityRole.COMMUNITY_ADMIN, MembershipStatus.ACTIVE);
        CommunityDetailResponseDTO detail = communityMapper.toDetailDto(community, myRole, memberCount, adminCount);
        lifecycle.describe(community, detail, requesterId, now);
        return detail;
    }

    @Override
    @Transactional
    public CommunityResponseDTO updateCommunity(Long communityId, CommunityUpdateRequestDTO dto,
                                                Long requesterId) {
        Community community = loadCommunityForUpdate(communityId);
        accessPolicy.verifyCommunityIsActive(community);
        accessPolicy.verifyRequesterIsAdmin(communityId, requesterId);

        String name = dto.getName().trim();

        if (communityRepository.existsByNameIgnoreCaseAndStatusAndIdNot(name, CommunityStatus.ACTIVE, communityId)) {
            throw new ConflictException("Community name already in use: " + name);
        }

        if (dto.getCommunityType() != null || dto.getCustomLabel() != null) {
            CommunityType type = dto.getCommunityType() != null ? dto.getCommunityType() : community.getCommunityType();
            String label = resolveCustomLabel(type, dto.getCustomLabel());
            community.setCommunityType(type);
            community.setCustomLabel(label);
        }

        community.setName(name);
        community.setDescription(dto.getDescription());

        long memberCount = membershipRepository
                .countByCommunityIdAndStatus(communityId, MembershipStatus.ACTIVE);

        return communityMapper.toCommunityDto(community, CommunityRole.COMMUNITY_ADMIN, memberCount);
    }

    @Override
    @Transactional
    public void archiveCommunity(Long communityId, Long requesterId) {
        Community community = loadCommunityForUpdate(communityId);
        accessPolicy.verifyRequesterIsAdmin(communityId, requesterId);
        LocalDateTime now = LocalDateTime.now();
        lifecycle.reconcile(community, now);
        if (community.getStatus() != CommunityStatus.DELETED) {
            lifecycle.delete(community, now);
        }
    }

    @Override
    @Transactional
    public Page<CommunityAdministrationCandidateDTO> getAdministrationCandidates(Long communityId, Long requesterId, Pageable pageable) {
        Community community = loadCommunityForUpdate(communityId);
        LocalDateTime now = LocalDateTime.now();
        verifyTransferWindow(community, requesterId, now);

        return membershipRepository.findAdministrationCandidates(communityId, MembershipStatus.ACTIVE, requesterId, pageable)
                .map(member -> {
                    Long id = member.getPlayer().getId();
                    boolean eligible = lifecycle.activePlan(id, now) != null && planLimits.canManageAnotherCommunity(id);
                    return new CommunityAdministrationCandidateDTO(id, member.getPlayer().getName(), eligible);
                });
    }

    @Override
    @Transactional
    public void transferAdministration(Long communityId, Long playerId, Long requesterId) {
        Community community = loadCommunityForUpdate(communityId);
        LocalDateTime now = LocalDateTime.now();
        verifyTransferWindow(community, requesterId, now);
        if (playerId.equals(requesterId)) {
            throw new InvalidCommunityException("Elige a otro miembro.");
        }
        CommunityMembership receivingMember = membershipRepository.findByCommunityIdAndPlayerId(communityId, playerId)
                .filter(member -> member.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> new InvalidCommunityException("Elige a un miembro activo de la comunidad."));
        CommunityMembership previousAdmin = membershipRepository.findByCommunityIdAndPlayerId(communityId, requesterId)
                .orElseThrow(() -> new InvalidCommunityException("No encontramos a la persona responsable."));
        playerRepository.lockForSpaceAllocation(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("No encontramos al jugador"));
        Membership plan = lifecycle.activePlan(playerId, now);
        if (plan == null) {
            throw new InvalidCommunityException("El nuevo administrador necesita un plan de pago activo.");
        }
        planLimits.verifyCreationLimit(playerId);
        receivingMember.setRole(CommunityRole.COMMUNITY_ADMIN);
        previousAdmin.setRole(CommunityRole.MEMBER);
        community.setResponsibleAdminId(playerId);
        community.setPlanExpiresAt(plan.getEndDate());
    }

    @Override
    public Page<CommunityMemberResponseDTO> getCommunityMembers(Long communityId, Pageable pageable,
                                                                Long requesterId) {
        return memberService.getCommunityMembers(communityId, pageable, requesterId);
    }

    @Override
    public Page<CommunityRankingEntryDTO> getCommunityRanking(Long communityId, Pageable pageable,
                                                              Long requesterId) {
        return memberService.getCommunityRanking(communityId, pageable, requesterId);
    }

    @Override
    @Transactional
    public CommunityMemberResponseDTO addMember(Long communityId, CommunityMemberAddRequestDTO dto,
                                                Long requesterId) {
        return memberService.addMember(communityId, dto, requesterId);
    }

    @Override
    @Transactional
    public CommunityMemberResponseDTO updateMemberRole(Long communityId, Long playerId,
                                                       CommunityMemberRoleUpdateDTO dto,
                                                       Long requesterId) {
        return memberService.updateMemberRole(communityId, playerId, dto, requesterId);
    }

    @Override
    @Transactional
    public void removeMember(Long communityId, Long playerId, Long requesterId) {
        memberService.removeMember(communityId, playerId, requesterId);
    }

    private void verifyTransferWindow(Community community, Long requesterId, LocalDateTime now) {
        accessPolicy.verifyRequesterIsAdmin(community.getId(), requesterId);
        if (!community.managingPlayerId().equals(requesterId)) {
            throw new UnauthorizedActionException("Solo la persona responsable puede transferir la administración.");
        }
        lifecycle.reconcile(community, now);
        if (community.getStatus() != CommunityStatus.ACTIVE) {
            throw new InvalidCommunityException("El plazo para transferir la administración terminó.");
        }
    }

    private Community loadCommunityForUpdate(Long communityId) {
        return communityRepository.findByIdForUpdate(communityId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Community not found with id: " + communityId));
    }

    private Player loadPlayer(Long playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Player not found with id: " + playerId));
    }

    private String resolveCustomLabel(CommunityType type, String label) {
        if (type != CommunityType.OTHER) {
            return null;
        }
        if (label == null || label.isBlank()) {
            throw new InvalidCommunityException("Escribe una etiqueta para el tipo Otro");
        }
        return label.strip();
    }

    private Community buildCommunity(CommunityCreateRequestDTO dto, String name, Player creator, String customLabel) {
        return Community.builder()
                .name(name)
                .description(dto.getDescription())
                .communityType(dto.getCommunityType())
                .customLabel(customLabel)
                .status(CommunityStatus.ACTIVE)
                .creator(creator)
                .build();
    }

    private void createAdminMembership(Community community, Player creator) {
        CommunityMembership membership = CommunityMembership.builder()
                .community(community)
                .player(creator)
                .role(CommunityRole.COMMUNITY_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        membershipRepository.save(membership);
    }

    private CommunityRole findRequesterRole(Long communityId, Long requesterId) {
        return membershipRepository.findByCommunityIdAndPlayerId(communityId, requesterId)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .map(CommunityMembership::getRole)
                .orElse(null);
    }

    private Map<Long, Long> countMembersByCommunity(List<Long> communityIds) {
        if (communityIds.isEmpty()) {
            return new HashMap<>();
        }

        return membershipRepository
                .countActiveMembersByCommunityIds(communityIds, MembershipStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));
    }
}

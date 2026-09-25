package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.Community.CommunityType;
import org.example.pongrankbackend.Community.dto.CommunityCreateRequestDTO;
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
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.service.MembershipService;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.CommunityMembershipException;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final ModelMapper modelMapper;
    private final MembershipService membershipService;

    @Value("${community.max-created.freemium}")
    private int maxCreatedFreemium;

    @Value("${community.max-created.basic}")
    private int maxCreatedBasic;

    @Value("${community.max-created.pro}")
    private int maxCreatedPro;

    @Value("${community.max-created.enterprise}")
    private int maxCreatedEnterprise;

    @Value("${community.max-total.freemium}")
    private int maxTotalFreemium;

    @Value("${community.max-total.basic}")
    private int maxTotalBasic;

    @Value("${community.max-total.pro}")
    private int maxTotalPro;

    @Value("${community.max-total.enterprise}")
    private int maxTotalEnterprise;

    public CommunityServiceImpl(CommunityRepository communityRepository,
                                CommunityMembershipRepository membershipRepository,
                                PlayerRepository playerRepository,
                                ModelMapper modelMapper,
                                MembershipService membershipService) {
        this.communityRepository = communityRepository;
        this.membershipRepository = membershipRepository;
        this.playerRepository = playerRepository;
        this.modelMapper = modelMapper;
        this.membershipService = membershipService;
    }

    @Override
    @Transactional
    public CommunityResponseDTO createCommunity(CommunityCreateRequestDTO dto, Long requesterId) {
        String name = dto.getName().trim();

        if (communityRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Community name already in use: " + name);
        }

        Player creator = loadPlayer(requesterId);
        verifyCreationLimit(requesterId);
        Community community = buildCommunity(dto, name, creator);

        try {
            Community saved = communityRepository.save(community);
            createAdminMembership(saved, creator);
            return toCommunityDto(saved, CommunityRole.COMMUNITY_ADMIN, 1L);
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

        return page.map(community -> toCommunityDto(
                community,
                findRequesterRole(community.getId(), requesterId),
                counts.getOrDefault(community.getId(), 0L)));
    }

    @Override
    public List<CommunityResponseDTO> getMyCommunities(Long requesterId) {
        List<CommunityMembership> memberships = membershipRepository
                .findByPlayerIdWithCommunity(requesterId, MembershipStatus.ACTIVE);

        List<Long> ids = memberships.stream()
                .map(m -> m.getCommunity().getId())
                .toList();
        Map<Long, Long> counts = countMembersByCommunity(ids);

        return memberships.stream()
                .map(m -> toCommunityDto(
                        m.getCommunity(),
                        m.getRole(),
                        counts.getOrDefault(m.getCommunity().getId(), 0L)))
                .toList();
    }

    @Override
    public CommunityDetailResponseDTO getCommunityById(Long communityId, Long requesterId) {
        Community community = communityRepository.findByIdWithCreator(communityId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Community not found with id: " + communityId));

        CommunityRole myRole = findRequesterRole(communityId, requesterId);
        long memberCount = membershipRepository
                .countByCommunityIdAndStatus(communityId, MembershipStatus.ACTIVE);

        return buildDetailDto(community, myRole, memberCount);
    }

    @Override
    @Transactional
    public CommunityResponseDTO updateCommunity(Long communityId, CommunityUpdateRequestDTO dto,
                                                Long requesterId) {
        Community community = loadCommunity(communityId);
        verifyCommunityIsActive(community);
        verifyRequesterIsAdmin(communityId, requesterId);

        String name = dto.getName().trim();

        if (communityRepository.existsByNameIgnoreCaseAndIdNot(name, communityId)) {
            throw new ConflictException("Community name already in use: " + name);
        }

        community.setName(name);
        community.setDescription(dto.getDescription());

        long memberCount = membershipRepository
                .countByCommunityIdAndStatus(communityId, MembershipStatus.ACTIVE);

        return toCommunityDto(community, CommunityRole.COMMUNITY_ADMIN, memberCount);
    }

    @Override
    @Transactional
    public void archiveCommunity(Long communityId, Long requesterId) {
        Community community = loadCommunity(communityId);
        verifyCommunityIsActive(community);
        verifyRequesterIsAdmin(communityId, requesterId);

        community.setStatus(CommunityStatus.ARCHIVED);
    }

    @Override
    public Page<CommunityMemberResponseDTO> getCommunityMembers(Long communityId, Pageable pageable,
                                                                Long requesterId) {
        loadCommunity(communityId);

        return membershipRepository
                .findMembersWithPlayer(communityId, MembershipStatus.ACTIVE, pageable)
                .map(this::toMemberDto);
    }

    @Override
    public Page<CommunityRankingEntryDTO> getCommunityRanking(Long communityId, Pageable pageable,
                                                              Long requesterId) {
        loadCommunity(communityId);

        Page<CommunityMembership> page = membershipRepository
                .findRankingWithPlayer(communityId, MembershipStatus.ACTIVE, pageable);

        int offset = (int) pageable.getOffset();
        List<CommunityRankingEntryDTO> entries = buildRankingEntries(page.getContent(), offset);

        return new PageImpl<>(entries, pageable, page.getTotalElements());
    }

    @Override
    @Transactional
    public CommunityMemberResponseDTO addMember(Long communityId, CommunityMemberAddRequestDTO dto,
                                                Long requesterId) {
        Community community = loadCommunityForUpdate(communityId);
        verifyCommunityIsActive(community);

        Long targetId = resolveTargetPlayer(communityId, dto, requesterId);
        verifyTotalMembershipLimit(targetId);

        return membershipRepository.findByCommunityIdAndPlayerId(communityId, targetId)
                .map(this::reactivateMembership)
                .orElseGet(() -> createMembership(community, targetId));
    }

    @Override
    @Transactional
    public CommunityMemberResponseDTO updateMemberRole(Long communityId, Long playerId,
                                                       CommunityMemberRoleUpdateDTO dto,
                                                       Long requesterId) {
        Community community = loadCommunityForUpdate(communityId);
        verifyCommunityIsActive(community);
        verifyRequesterIsAdmin(communityId, requesterId);

        CommunityMembership membership = loadActiveMembership(communityId, playerId);

        if (dto.getRole() != CommunityRole.COMMUNITY_ADMIN) {
            verifyNotLastAdmin(communityId, membership);
        }

        membership.setRole(dto.getRole());
        return toMemberDto(membership);
    }

    @Override
    @Transactional
    public void removeMember(Long communityId, Long playerId, Long requesterId) {
        Community community = loadCommunityForUpdate(communityId);

        boolean isSelfRemoval = playerId.equals(requesterId);
        if (!isSelfRemoval) {
            verifyRequesterIsAdmin(communityId, requesterId);
        }

        CommunityMembership membership = loadActiveMembership(communityId, playerId);

        long activeMembers = membershipRepository
                .countByCommunityIdAndStatus(communityId, MembershipStatus.ACTIVE);

        if (activeMembers > 1) {
            verifyNotLastAdmin(communityId, membership);
        }

        deactivateMembership(membership);

        if (activeMembers == 1) {
            community.setStatus(CommunityStatus.ARCHIVED);
        }
    }

    private Community loadCommunity(Long communityId) {
        return communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Community not found with id: " + communityId));
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

    private CommunityMembership loadMembership(Long communityId, Long playerId) {
        return membershipRepository.findByCommunityIdAndPlayerId(communityId, playerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membership not found for player " + playerId + " in community " + communityId));
    }

    private CommunityMembership loadActiveMembership(Long communityId, Long playerId) {
        CommunityMembership membership = loadMembership(communityId, playerId);

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ResourceNotFoundException(
                    "Player " + playerId + " is not an active member of community " + communityId);
        }

        return membership;
    }

    private void verifyRequesterIsAdmin(Long communityId, Long requesterId) {
        boolean isAdmin = membershipRepository.existsByCommunityIdAndPlayerIdAndRoleAndStatus(
                communityId, requesterId, CommunityRole.COMMUNITY_ADMIN, MembershipStatus.ACTIVE);

        if (!isAdmin) {
            throw new UnauthorizedActionException(
                    "Only community administrators can perform this action");
        }
    }

    private void verifyCreationLimit(Long playerId) {
        int limit = maxCreatedFor(membershipService.getActivePlan(playerId));

        long createdActive = communityRepository.countByCreatorIdAndStatus(playerId, CommunityStatus.ACTIVE);

        if (createdActive >= limit) {
            throw new CommunityMembershipException(
                    "Player " + playerId + " reached the maximum of " + limit + " active communities created for their plan");
        }
    }

    private void verifyTotalMembershipLimit(Long playerId) {
        int limit = maxTotalFor(membershipService.getActivePlan(playerId));

        long createdActive = communityRepository.countByCreatorIdAndStatus(playerId, CommunityStatus.ACTIVE);
        long activeMemberships = membershipRepository.countByPlayerIdAndRoleAndStatus(
                playerId, CommunityRole.MEMBER, MembershipStatus.ACTIVE);

        if (createdActive + activeMemberships >= limit) {
            throw new CommunityMembershipException(
                    "Player " + playerId + " reached the maximum of " + limit + " total communities for their plan");
        }
    }

    private int maxCreatedFor(MembershipPlan plan) {
        return switch (plan) {
            case FREEMIUM -> maxCreatedFreemium;
            case BASIC -> maxCreatedBasic;
            case PRO -> maxCreatedPro;
            case ENTERPRISE -> maxCreatedEnterprise;
        };
    }

    private int maxTotalFor(MembershipPlan plan) {
        return switch (plan) {
            case FREEMIUM -> maxTotalFreemium;
            case BASIC -> maxTotalBasic;
            case PRO -> maxTotalPro;
            case ENTERPRISE -> maxTotalEnterprise;
        };
    }

    private void verifyCommunityIsActive(Community community) {
        if (community.getStatus() == CommunityStatus.ARCHIVED) {
            throw new CommunityMembershipException(
                    "Community is archived and cannot be modified");
        }
    }

    private void verifyNotLastAdmin(Long communityId, CommunityMembership target) {
        boolean targetIsActiveAdmin = target.getRole() == CommunityRole.COMMUNITY_ADMIN
                && target.getStatus() == MembershipStatus.ACTIVE;

        if (!targetIsActiveAdmin) {
            return;
        }

        long activeAdmins = membershipRepository.countByCommunityIdAndRoleAndStatus(
                communityId, CommunityRole.COMMUNITY_ADMIN, MembershipStatus.ACTIVE);

        if (activeAdmins <= 1) {
            throw new CommunityMembershipException(
                    "Cannot remove the last administrator. Promote another member first");
        }
    }

    private Long resolveTargetPlayer(Long communityId, CommunityMemberAddRequestDTO dto,
                                     Long requesterId) {
        if (dto == null || dto.getPlayerId() == null) {
            return requesterId;
        }

        if (!dto.getPlayerId().equals(requesterId)) {
            verifyRequesterIsAdmin(communityId, requesterId);
        }

        return dto.getPlayerId();
    }

    private CommunityMemberResponseDTO reactivateMembership(CommunityMembership membership) {
        if (membership.getStatus() == MembershipStatus.ACTIVE) {
            throw new CommunityMembershipException("Player is already a member of this community");
        }

        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setLeftAt(null);
        return toMemberDto(membership);
    }

    private CommunityMemberResponseDTO createMembership(Community community, Long playerId) {
        CommunityMembership membership = CommunityMembership.builder()
                .community(community)
                .player(loadPlayer(playerId))
                .role(CommunityRole.MEMBER)
                .status(MembershipStatus.ACTIVE)
                .build();

        return toMemberDto(membershipRepository.save(membership));
    }

    private void deactivateMembership(CommunityMembership membership) {
        membership.setStatus(MembershipStatus.INACTIVE);
        membership.setLeftAt(LocalDateTime.now());
    }

    private Community buildCommunity(CommunityCreateRequestDTO dto, String name, Player creator) {
        return Community.builder()
                .name(name)
                .description(dto.getDescription())
                .communityType(dto.getCommunityType())
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

    private CommunityResponseDTO toCommunityDto(Community community, CommunityRole myRole,
                                                long memberCount) {
        CommunityResponseDTO dto = modelMapper.map(community, CommunityResponseDTO.class);
        dto.setMyRole(myRole);
        dto.setMemberCount(memberCount);
        return dto;
    }

    private CommunityDetailResponseDTO buildDetailDto(Community community, CommunityRole myRole,
                                                      long memberCount) {
        return CommunityDetailResponseDTO.builder()
                .id(community.getId())
                .name(community.getName())
                .description(community.getDescription())
                .communityType(community.getCommunityType())
                .status(community.getStatus())
                .memberCount(memberCount)
                .creatorId(community.getCreator().getId())
                .creatorName(community.getCreator().getName())
                .isMember(myRole != null)
                .myRole(myRole)
                .createdAt(community.getCreatedAt())
                .build();
    }

    private CommunityMemberResponseDTO toMemberDto(CommunityMembership membership) {
        Player player = membership.getPlayer();

        return CommunityMemberResponseDTO.builder()
                .membershipId(membership.getId())
                .playerId(player.getId())
                .playerName(player.getName())
                .ratingGlicko(player.getRatingGlicko())
                .role(membership.getRole())
                .status(membership.getStatus())
                .joinedAt(membership.getJoinedAt())
                .build();
    }

    private CommunityRankingEntryDTO toRankingDto(CommunityMembership membership, int position) {
        Player player = membership.getPlayer();

        return CommunityRankingEntryDTO.builder()
                .position(position)
                .playerId(player.getId())
                .playerName(player.getName())
                .ratingGlicko(player.getRatingGlicko())
                .ratingDeviation(player.getRatingDeviation())
                .build();
    }

    private List<CommunityRankingEntryDTO> buildRankingEntries(List<CommunityMembership> memberships,
                                                               int offset) {
        List<CommunityRankingEntryDTO> entries = new ArrayList<>();

        for (int i = 0; i < memberships.size(); i++) {
            entries.add(toRankingDto(memberships.get(i), offset + i + 1));
        }

        return entries;
    }
}
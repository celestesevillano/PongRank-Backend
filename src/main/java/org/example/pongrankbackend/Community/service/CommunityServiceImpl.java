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
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public CommunityServiceImpl(CommunityRepository communityRepository,
                                CommunityMembershipRepository membershipRepository,
                                PlayerRepository playerRepository,
                                CommunityMapper communityMapper,
                                CommunityAccessPolicy accessPolicy,
                                CommunityPlanLimits planLimits,
                                CommunityMemberService memberService) {
        this.communityRepository = communityRepository;
        this.membershipRepository = membershipRepository;
        this.playerRepository = playerRepository;
        this.communityMapper = communityMapper;
        this.accessPolicy = accessPolicy;
        this.planLimits = planLimits;
        this.memberService = memberService;
    }

    @Override
    @Transactional
    public CommunityResponseDTO createCommunity(CommunityCreateRequestDTO dto, Long requesterId) {
        String name = dto.getName().trim();

        if (communityRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Community name already in use: " + name);
        }

        Player creator = loadPlayer(requesterId);
        planLimits.verifyCreationLimit(requesterId);
        Community community = buildCommunity(dto, name, creator);

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
                .findByPlayerIdWithCommunity(requesterId, MembershipStatus.ACTIVE);

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
    public CommunityDetailResponseDTO getCommunityById(Long communityId, Long requesterId) {
        Community community = communityRepository.findByIdWithCreator(communityId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Community not found with id: " + communityId));

        CommunityRole myRole = findRequesterRole(communityId, requesterId);
        long memberCount = membershipRepository
                .countByCommunityIdAndStatus(communityId, MembershipStatus.ACTIVE);

        return communityMapper.toDetailDto(community, myRole, memberCount);
    }

    @Override
    @Transactional
    public CommunityResponseDTO updateCommunity(Long communityId, CommunityUpdateRequestDTO dto,
                                                Long requesterId) {
        Community community = loadCommunity(communityId);
        accessPolicy.verifyCommunityIsActive(community);
        accessPolicy.verifyRequesterIsAdmin(communityId, requesterId);

        String name = dto.getName().trim();

        if (communityRepository.existsByNameIgnoreCaseAndIdNot(name, communityId)) {
            throw new ConflictException("Community name already in use: " + name);
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
        Community community = loadCommunity(communityId);
        accessPolicy.verifyCommunityIsActive(community);
        accessPolicy.verifyRequesterIsAdmin(communityId, requesterId);

        community.setStatus(CommunityStatus.ARCHIVED);
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

    private Community loadCommunity(Long communityId) {
        return communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Community not found with id: " + communityId));
    }

    private Player loadPlayer(Long playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Player not found with id: " + playerId));
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
}

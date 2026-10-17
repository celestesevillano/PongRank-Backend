package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.Community.repository.CommunityRepository;
import org.example.pongrankbackend.CommunityMembership.CommunityMembership;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.CommunityMembership.MembershipStatus;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberAddRequestDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberResponseDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberRoleUpdateDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityRankingEntryDTO;
import org.example.pongrankbackend.CommunityMembership.repository.CommunityMembershipRepository;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.CommunityMembershipException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CommunityMemberServiceImpl implements CommunityMemberService {

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository membershipRepository;
    private final PlayerRepository playerRepository;
    private final CommunityMapper communityMapper;
    private final CommunityAccessPolicy accessPolicy;
    private final CommunityPlanLimits planLimits;

    public CommunityMemberServiceImpl(CommunityRepository communityRepository,
                                      CommunityMembershipRepository membershipRepository,
                                      PlayerRepository playerRepository,
                                      CommunityMapper communityMapper,
                                      CommunityAccessPolicy accessPolicy,
                                      CommunityPlanLimits planLimits) {
        this.communityRepository = communityRepository;
        this.membershipRepository = membershipRepository;
        this.playerRepository = playerRepository;
        this.communityMapper = communityMapper;
        this.accessPolicy = accessPolicy;
        this.planLimits = planLimits;
    }

    @Override
    public Page<CommunityMemberResponseDTO> getCommunityMembers(Long communityId, Pageable pageable,
                                                                Long requesterId) {
        loadCommunity(communityId);

        return membershipRepository
                .findMembersWithPlayer(communityId, MembershipStatus.ACTIVE, pageable)
                .map(communityMapper::toMemberDto);
    }

    @Override
    public Page<CommunityRankingEntryDTO> getCommunityRanking(Long communityId, Pageable pageable,
                                                              Long requesterId) {
        loadCommunity(communityId);

        Page<CommunityMembership> page = membershipRepository
                .findRankingWithPlayer(communityId, MembershipStatus.ACTIVE, pageable);

        int offset = (int) pageable.getOffset();
        List<CommunityRankingEntryDTO> entries = communityMapper.toRankingEntries(page.getContent(), offset);

        return new PageImpl<>(entries, pageable, page.getTotalElements());
    }

    @Override
    @Transactional
    public CommunityMemberResponseDTO addMember(Long communityId, CommunityMemberAddRequestDTO dto,
                                                Long requesterId) {
        Community community = loadCommunityForUpdate(communityId);
        accessPolicy.verifyCommunityIsActive(community);

        Long targetId = resolveTargetPlayer(communityId, dto, requesterId);
        planLimits.verifyTotalMembershipLimit(targetId);

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
        accessPolicy.verifyCommunityIsActive(community);
        accessPolicy.verifyRequesterIsAdmin(communityId, requesterId);

        CommunityMembership membership = loadActiveMembership(communityId, playerId);

        if (dto.getRole() != CommunityRole.COMMUNITY_ADMIN) {
            verifyNotLastAdmin(communityId, membership);
        }

        membership.setRole(dto.getRole());
        return communityMapper.toMemberDto(membership);
    }

    @Override
    @Transactional
    public void removeMember(Long communityId, Long playerId, Long requesterId) {
        Community community = loadCommunityForUpdate(communityId);

        boolean isSelfRemoval = playerId.equals(requesterId);
        if (!isSelfRemoval) {
            accessPolicy.verifyRequesterIsAdmin(communityId, requesterId);
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
            accessPolicy.verifyRequesterIsAdmin(communityId, requesterId);
        }

        return dto.getPlayerId();
    }

    private CommunityMemberResponseDTO reactivateMembership(CommunityMembership membership) {
        if (membership.getStatus() == MembershipStatus.ACTIVE) {
            throw new CommunityMembershipException("Player is already a member of this community");
        }

        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setLeftAt(null);
        return communityMapper.toMemberDto(membership);
    }

    private CommunityMemberResponseDTO createMembership(Community community, Long playerId) {
        CommunityMembership membership = CommunityMembership.builder()
                .community(community)
                .player(playerRepository.findById(playerId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Player not found with id: " + playerId)))
                .role(CommunityRole.MEMBER)
                .status(MembershipStatus.ACTIVE)
                .build();

        return communityMapper.toMemberDto(membershipRepository.save(membership));
    }

    private void deactivateMembership(CommunityMembership membership) {
        membership.setStatus(MembershipStatus.INACTIVE);
        membership.setLeftAt(LocalDateTime.now());
    }
}

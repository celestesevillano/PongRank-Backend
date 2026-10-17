package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.CommunityMembership.MembershipStatus;
import org.example.pongrankbackend.CommunityMembership.repository.CommunityMembershipRepository;
import org.example.pongrankbackend.common.exception.CommunityMembershipException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.springframework.stereotype.Component;

@Component
public class CommunityAccessPolicy {

    private final CommunityMembershipRepository membershipRepository;

    public CommunityAccessPolicy(CommunityMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public void verifyRequesterIsAdmin(Long communityId, Long requesterId) {
        boolean isAdmin = membershipRepository.existsByCommunityIdAndPlayerIdAndRoleAndStatus(
                communityId, requesterId, CommunityRole.COMMUNITY_ADMIN, MembershipStatus.ACTIVE);

        if (!isAdmin) {
            throw new UnauthorizedActionException(
                    "Only community administrators can perform this action");
        }
    }

    public void verifyCommunityIsActive(Community community) {
        if (community.getStatus() == CommunityStatus.ARCHIVED) {
            throw new CommunityMembershipException(
                    "Community is archived and cannot be modified");
        }
    }
}

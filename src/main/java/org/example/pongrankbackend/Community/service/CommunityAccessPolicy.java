package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.CommunityMembership.MembershipStatus;
import org.example.pongrankbackend.CommunityMembership.repository.CommunityMembershipRepository;
import org.example.pongrankbackend.common.exception.CommunityMembershipException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CommunityAccessPolicy {

    private final CommunityMembershipRepository membershipRepository;

    private final CommunityLifecycle lifecycle;

    public CommunityAccessPolicy(CommunityMembershipRepository membershipRepository, CommunityLifecycle lifecycle) {
        this.membershipRepository = membershipRepository;
        this.lifecycle = lifecycle;
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
        LocalDateTime now = LocalDateTime.now();
        lifecycle.reconcile(community, now);
        if (community.getStatus() != CommunityStatus.ACTIVE || lifecycle.inGrace(community, now)) {
            throw new CommunityMembershipException("La gestión de esta comunidad está pausada.");
        }
    }
}

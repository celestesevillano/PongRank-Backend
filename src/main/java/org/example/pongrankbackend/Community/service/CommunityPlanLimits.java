package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.Community.repository.CommunityRepository;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.CommunityMembership.MembershipStatus;
import org.example.pongrankbackend.CommunityMembership.repository.CommunityMembershipRepository;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.service.MembershipService;
import org.example.pongrankbackend.common.exception.PlanRestrictionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CommunityPlanLimits {

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository membershipRepository;
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

    public CommunityPlanLimits(CommunityRepository communityRepository,
                               CommunityMembershipRepository membershipRepository,
                               MembershipService membershipService) {
        this.communityRepository = communityRepository;
        this.membershipRepository = membershipRepository;
        this.membershipService = membershipService;
    }

    public void verifyCreationLimit(Long playerId) {
        int limit = maxCreatedFor(membershipService.getActivePlan(playerId));

        long createdActive = communityRepository.countByCreatorIdAndStatus(playerId, CommunityStatus.ACTIVE);

        if (createdActive >= limit) {
            throw new PlanRestrictionException(
                    "Player " + playerId + " reached the maximum of " + limit + " active communities created for their plan");
        }
    }

    public void verifyTotalMembershipLimit(Long playerId) {
        int limit = maxTotalFor(membershipService.getActivePlan(playerId));

        long createdActive = communityRepository.countByCreatorIdAndStatus(playerId, CommunityStatus.ACTIVE);
        long activeMemberships = membershipRepository.countByPlayerIdAndRoleAndStatus(
                playerId, CommunityRole.MEMBER, MembershipStatus.ACTIVE);

        if (createdActive + activeMemberships >= limit) {
            throw new PlanRestrictionException(
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
}

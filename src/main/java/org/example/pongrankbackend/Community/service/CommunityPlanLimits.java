package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.Community.repository.CommunityRepository;
import org.example.pongrankbackend.Membership.service.SpaceQuotaService;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.service.MembershipService;
import org.example.pongrankbackend.common.exception.PlanRestrictionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CommunityPlanLimits {

    private final CommunityRepository communityRepository;
    private final SpaceQuotaService spaces;
    private final MembershipService membershipService;

    @Value("${community.max-created.freemium}")
    private int maxCreatedFreemium;

    @Value("${community.max-created.basic}")
    private int maxCreatedBasic;

    @Value("${community.max-created.pro}")
    private int maxCreatedPro;

    @Value("${community.max-created.enterprise}")
    private int maxCreatedEnterprise;

    public CommunityPlanLimits(CommunityRepository communityRepository,
                               SpaceQuotaService spaces,
                               MembershipService membershipService) {
        this.communityRepository = communityRepository;
        this.spaces = spaces;
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
        spaces.requireAvailable(playerId);
    }

    private int maxCreatedFor(MembershipPlan plan) {
        return switch (plan) {
            case FREEMIUM -> maxCreatedFreemium;
            case BASIC -> maxCreatedBasic;
            case PRO -> maxCreatedPro;
            case ENTERPRISE -> maxCreatedEnterprise;
        };
    }

}

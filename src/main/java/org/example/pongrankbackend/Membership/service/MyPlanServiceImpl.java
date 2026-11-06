package org.example.pongrankbackend.Membership.service;

import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.dto.MyPlanResponseDTO;
import org.example.pongrankbackend.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MyPlanServiceImpl implements MyPlanService {

    private final MembershipService membershipService;

    @Value("${community.max-created.freemium}")
    private int createdFreemium;
    @Value("${community.max-created.basic}")
    private int createdBasic;
    @Value("${community.max-created.pro}")
    private int createdPro;
    @Value("${community.max-created.enterprise}")
    private int createdEnterprise;

    @Value("${community.max-total.freemium}")
    private int communitiesFreemium;
    @Value("${community.max-total.basic}")
    private int communitiesBasic;
    @Value("${community.max-total.pro}")
    private int communitiesPro;
    @Value("${community.max-total.enterprise}")
    private int communitiesEnterprise;

    @Value("${club.max-total.freemium}")
    private int clubsFreemium;
    @Value("${club.max-total.basic}")
    private int clubsBasic;
    @Value("${club.max-total.pro}")
    private int clubsPro;
    @Value("${club.max-total.enterprise}")
    private int clubsEnterprise;

    public MyPlanServiceImpl(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Override
    public MyPlanResponseDTO getMyPlan() {
        Long playerId = SecurityUtils.getRequiredCurrentUserId();
        MembershipPlan plan = membershipService.getActivePlan(playerId);
        return MyPlanResponseDTO.builder()
                .plan(plan)
                .endDate(membershipService.getActivePlanEndDate(playerId).orElse(null))
                .coachAccess(membershipService.hasCoachAccess(playerId))
                .canCreateClub(membershipService.canCreateClub(playerId))
                .maxCommunitiesCreated(pick(plan, createdFreemium, createdBasic, createdPro, createdEnterprise))
                .maxCommunitiesTotal(pick(plan, communitiesFreemium, communitiesBasic, communitiesPro, communitiesEnterprise))
                .maxClubsTotal(pick(plan, clubsFreemium, clubsBasic, clubsPro, clubsEnterprise))
                .build();
    }

    private int pick(MembershipPlan plan, int freemium, int basic, int pro, int enterprise) {
        return switch (plan) {
            case FREEMIUM -> freemium;
            case BASIC -> basic;
            case PRO -> pro;
            case ENTERPRISE -> enterprise;
        };
    }
}

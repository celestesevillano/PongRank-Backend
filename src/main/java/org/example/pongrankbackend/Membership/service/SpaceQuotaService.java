package org.example.pongrankbackend.Membership.service;

import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.ClubMembership.ClubMembershipStatus;
import org.example.pongrankbackend.ClubMembership.repository.ClubMembershipRepository;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.CommunityMembership.MembershipStatus;
import org.example.pongrankbackend.CommunityMembership.repository.CommunityMembershipRepository;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.PlanRestrictionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SpaceQuotaService {
    private final PlayerRepository players;
    private final CommunityMembershipRepository communityMemberships;
    private final ClubRepository clubs;
    private final ClubMembershipRepository clubMemberships;
    private final MembershipService memberships;

    @Value("${space.max-total.freemium}")
    private int maxFree;
    @Value("${space.max-total.basic}")
    private int maxBasic;
    @Value("${space.max-total.pro}")
    private int maxPro;
    @Value("${space.max-total.enterprise}")
    private int maxEnterprise;

    public SpaceQuotaService(PlayerRepository players, CommunityMembershipRepository communityMemberships,
                             ClubRepository clubs, ClubMembershipRepository clubMemberships,
                             MembershipService memberships) {
        this.players = players;
        this.communityMemberships = communityMemberships;
        this.clubs = clubs;
        this.clubMemberships = clubMemberships;
        this.memberships = memberships;
    }

    public int limit(MembershipPlan plan) {
        return switch (plan) {
            case FREEMIUM -> maxFree;
            case BASIC -> maxBasic;
            case PRO -> maxPro;
            case ENTERPRISE -> maxEnterprise;
        };
    }

    public long used(Long playerId) {
        long communities = communityMemberships.countByPlayerIdAndStatusAndCommunityStatus(
                playerId, MembershipStatus.ACTIVE, CommunityStatus.ACTIVE);
        long managedClubs = clubs.countByAdminIdAndStatusIn(playerId, ClubStatus.ACTIVE_STATUSES);
        long otherClubs = clubMemberships.countDistinctOtherClubs(
                playerId, ClubMembershipStatus.ACTIVE_STATUSES, ClubStatus.APPROVED);
        return communities + managedClubs + otherClubs;
    }

    // Each caller keeps this lock until the membership or club is saved.
    public void requireAvailable(Long playerId) {
        players.lockForSpaceAllocation(playerId);
        int max = limit(memberships.getActivePlan(playerId));
        if (used(playerId) >= max) {
            throw new PlanRestrictionException("Ya usaste tus " + max + " espacios.");
        }
    }
}

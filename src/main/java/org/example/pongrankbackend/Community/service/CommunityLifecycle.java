package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.Community.dto.CommunityDetailResponseDTO;
import org.example.pongrankbackend.Community.repository.CommunityRepository;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.Membership.Membership;
import org.example.pongrankbackend.Membership.MembershipStatus;
import org.example.pongrankbackend.Membership.repository.MembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CommunityLifecycle {
    private final CommunityRepository communities;
    private final MembershipRepository plans;

    public CommunityLifecycle(CommunityRepository communities, MembershipRepository plans) {
        this.communities = communities;
        this.plans = plans;
    }

    @Transactional
    public void reconcile(Long communityId) {
        communities.findByIdForUpdate(communityId).ifPresent(community -> reconcile(community, LocalDateTime.now()));
    }

    public void reconcile(Community community, LocalDateTime now) {
        if (community.getStatus() == CommunityStatus.DELETED) {
            return;
        }

        LocalDateTime expires = community.getPlanExpiresAt();
        if (community.getStatus() == CommunityStatus.ARCHIVED && expires == null) {
            return; // Previous manual closures remain final.
        }

        Membership plan = activePlan(community.managingPlayerId(), now);
        if (expires != null) {
            LocalDateTime deletesAt = expires.plusDays(4);
            if (!now.isBefore(deletesAt) && (plan == null || plan.getStartDate() == null
                    || !plan.getStartDate().isBefore(deletesAt))) {
                delete(community, deletesAt);
                return;
            }
        }

        if (plan != null) {
            community.setPlanExpiresAt(plan.getEndDate());
            community.setArchivedAt(null);
            community.setStatus(CommunityStatus.ACTIVE);
            return;
        }

        if (expires == null) {
            // Give existing communities the full grace period when first enrolled.
            community.setPlanExpiresAt(now);
        } else if (!now.isBefore(expires.plusDays(1))) {
            community.setStatus(CommunityStatus.ARCHIVED);
            community.setArchivedAt(expires.plusDays(1));
        }
    }

    Membership activePlan(Long playerId, LocalDateTime now) {
        return plans.findByPlayerIdAndStatus(playerId, MembershipStatus.ACTIVE)
                .filter(plan -> plan.getEndDate() != null && plan.getEndDate().isAfter(now))
                .orElse(null);
    }

    public void delete(Community community, LocalDateTime now) {
        community.setStatus(CommunityStatus.DELETED);
        community.setDeletedAt(now);
    }

    boolean inGrace(Community community, LocalDateTime now) {
        return community.getStatus() == CommunityStatus.ACTIVE && community.getPlanExpiresAt() != null
                && !now.isBefore(community.getPlanExpiresAt());
    }

    void describe(Community community, CommunityDetailResponseDTO dto, Long requesterId, LocalDateTime now) {
        LocalDateTime expires = community.getPlanExpiresAt();
        boolean responsible = community.managingPlayerId().equals(requesterId)
                && dto.getMyRole() == CommunityRole.COMMUNITY_ADMIN;
        dto.setServerTime(now);
        dto.setCanTransferAdministration(responsible && community.getStatus() == CommunityStatus.ACTIVE);
        if (community.getStatus() == CommunityStatus.DELETED
                || (community.getStatus() == CommunityStatus.ARCHIVED && expires == null)) {
            dto.setStatus(CommunityStatus.DELETED);
            dto.setLifecyclePhase("DELETED");
            dto.setDeletedAt(community.getDeletedAt() != null ? community.getDeletedAt() : community.getArchivedAt());
        } else if (community.getStatus() == CommunityStatus.ARCHIVED) {
            dto.setLifecyclePhase("ARCHIVED");
            dto.setArchivedAt(community.getArchivedAt());
            dto.setDeletesAt(expires.plusDays(4));
        } else if (inGrace(community, now)) {
            dto.setLifecyclePhase("GRACE");
            dto.setGraceEndsAt(expires.plusDays(1));
        } else {
            dto.setLifecyclePhase("ACTIVE");
        }
        if (responsible && community.getStatus() != CommunityStatus.DELETED) {
            dto.setPlanExpiresAt(expires);
        }
    }
}

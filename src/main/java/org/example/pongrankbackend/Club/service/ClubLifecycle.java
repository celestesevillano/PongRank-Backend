package org.example.pongrankbackend.Club.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.Membership.Membership;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.MembershipStatus;
import org.example.pongrankbackend.Membership.repository.MembershipRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClubLifecycle {
    public static final List<ClubStatus> TRACKED = List.of(ClubStatus.APPROVED, ClubStatus.PAUSED);

    private final ClubRepository clubs;
    private final MembershipRepository plans;
    private final long graceHours;

    public ClubLifecycle(ClubRepository clubs, MembershipRepository plans,
                         @Value("${club.lifecycle.grace-hours:72}") long graceHours) {
        this.clubs = clubs;
        this.plans = plans;
        this.graceHours = graceHours;
    }

    @Transactional
    public void reconcile(Long clubId) {
        clubs.findByIdForUpdate(clubId).ifPresent(club -> reconcile(club, LocalDateTime.now()));
    }

    void reconcile(Club club, LocalDateTime now) {
        if (!TRACKED.contains(club.getStatus())) return;

        Membership enterprise = plans.findByPlayerIdAndStatus(club.getAdmin().getId(), MembershipStatus.ACTIVE)
                .filter(plan -> plan.getPlan() == MembershipPlan.ENTERPRISE)
                .filter(plan -> plan.getEndDate() != null && plan.getEndDate().isAfter(now))
                .orElse(null);
        LocalDateTime expiry = club.getPlanExpiresAt();
        if (expiry != null) {
            LocalDateTime deadline = expiry.plusHours(graceHours);
            if (!now.isBefore(deadline) && (enterprise == null || enterprise.getStartDate() == null
                    || !enterprise.getStartDate().isBefore(deadline))) {
                club.setStatus(ClubStatus.DELETED);
                return;
            }
        }

        if (enterprise != null) {
            club.setStatus(ClubStatus.APPROVED);
            club.setPlanExpiresAt(enterprise.getEndDate());
            return;
        }

        // Give existing clubs with no stored deadline a full grace period on first reconciliation.
        if (expiry == null || expiry.isAfter(now)) {
            club.setPlanExpiresAt(now);
        }
        club.setStatus(ClubStatus.PAUSED);
    }
}

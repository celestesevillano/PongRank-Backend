package org.example.pongrankbackend.Membership.service;

import org.example.pongrankbackend.Membership.Membership;
import org.example.pongrankbackend.Membership.MembershipPlan;

public interface MembershipService {

    // Plan efectivo del jugador: FREEMIUM si nunca tuvo membresía o su plan pagado ya venció
    MembershipPlan getActivePlan(Long playerId);

    boolean isPaidMember(Long playerId);

    boolean hasCoachAccess(Long playerId);

    boolean canCreateClub(Long playerId);

    // Internal operations, no expuestas por HTTP (las usa PaymentService y el scheduler de vencimiento)

    Membership getOrCreatePendingMembership(Long playerId, MembershipPlan plan);

    void activatePaidMembership(Membership membership);

    void expireOverdueMemberships();
}

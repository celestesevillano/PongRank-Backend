package org.example.pongrankbackend.Membership.service;

import org.example.pongrankbackend.Membership.Membership;
import org.example.pongrankbackend.Membership.MembershipPlan;

public interface MembershipService {

    boolean isPremiumMember(Long playerId);

    // Internal operations, no expuestas por HTTP (las usa PaymentService y el scheduler de vencimiento)

    Membership getOrCreatePendingMembership(Long playerId, MembershipPlan plan);

    void activatePremiumMembership(Membership membership);

    void expireOverdueMemberships();
}

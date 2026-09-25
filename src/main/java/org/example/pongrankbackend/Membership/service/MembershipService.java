package org.example.pongrankbackend.Membership.service;

import org.example.pongrankbackend.Membership.Membership;
import org.example.pongrankbackend.Membership.MembershipPlan;

public interface MembershipService {

    boolean isPremiumMember(Long playerId);

    // Internal operations used by PaymentService (not exposed through HTTP)

    Membership getOrCreatePendingMembership(Long playerId, MembershipPlan plan);

    void activatePremiumMembership(Membership membership);
}

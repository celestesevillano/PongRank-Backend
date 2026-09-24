package org.example.pongrankbackend.ClubMembership;

import java.util.List;

public enum ClubMembershipStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    LEFT;

    // A player can hold at most one membership in these statuses (single-club rule)
    public static final List<ClubMembershipStatus> ACTIVE_STATUSES = List.of(PENDING, APPROVED);
}

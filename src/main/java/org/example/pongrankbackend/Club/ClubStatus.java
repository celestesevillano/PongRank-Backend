package org.example.pongrankbackend.Club;

import java.util.List;

public enum ClubStatus {
    PENDING,
    APPROVED,
    REJECTED;

    // A club in these statuses is still "in progress" for its admin (under review or approved)
    public static final List<ClubStatus> ACTIVE_STATUSES = List.of(PENDING, APPROVED);
}

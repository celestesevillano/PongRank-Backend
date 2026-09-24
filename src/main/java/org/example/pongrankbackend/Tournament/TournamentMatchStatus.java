package org.example.pongrankbackend.Tournament;

import java.util.List;

public enum TournamentMatchStatus {
    PENDING_PLAYERS,
    SCHEDULED,
    COMPLETED,
    WALKOVER,
    BYE;

    public static final List<TournamentMatchStatus> FINISHED_STATUSES = List.of(COMPLETED, WALKOVER, BYE);

    public boolean isFinished() {
        return FINISHED_STATUSES.contains(this);
    }
}

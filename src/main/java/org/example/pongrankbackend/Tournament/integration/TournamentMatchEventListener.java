package org.example.pongrankbackend.Tournament.integration;

import org.example.pongrankbackend.Match.event.MatchConfirmedEvent;
import org.example.pongrankbackend.Tournament.service.TournamentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/*
 * Keeps the bracket up to date automatically: when the Match module confirms a match (after its transaction
 * commits), Tournament records the result and advances the winner. A tournament problem never undoes the
 * Match confirmation; it is logged and can be retried with POST /api/v1/tournaments/{id}/sync-results.
 */
@Component
public class TournamentMatchEventListener {

    private static final Logger log = LoggerFactory.getLogger(TournamentMatchEventListener.class);

    private final TournamentService tournamentService;

    public TournamentMatchEventListener(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMatchConfirmed(MatchConfirmedEvent event) {
        try {
            tournamentService.applyConfirmedMatch(event.getMatchId());
        } catch (RuntimeException e) {
            log.warn("No se pudo aplicar el partido {} al torneo: {}", event.getMatchId(), e.getMessage());
        }
    }
}

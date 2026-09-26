package org.example.pongrankbackend.Match.event;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.rating.Glicko2Service;
import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MatchRatingEventListener {

    private static final Logger log = LoggerFactory.getLogger(MatchRatingEventListener.class);

    private final Glicko2Service glicko2Service;
    private final MatchRepository matchRepository;

    public MatchRatingEventListener(Glicko2Service glicko2Service,
                                    MatchRepository matchRepository) {
        this.glicko2Service = glicko2Service;
        this.matchRepository = matchRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMatchConfirmed(MatchConfirmedEvent event) {
        log.info("Procesando cálculo asíncrono Glicko-2 para el partido ID: {}", event.getMatchId());

        Match match = matchRepository.findById(event.getMatchId()).orElse(null);
        if (match == null) {
            log.warn("No se encontró el partido ID {} para el cálculo de rating", event.getMatchId());
            return;
        }

        try {
            glicko2Service.applyMatchRatingUpdate(match, event.isPlayer1Won());
            log.info("Cálculo Glicko-2 completado con éxito para partido ID {}", event.getMatchId());
        } catch (Exception e) {
            log.error("Error al calcular rating Glicko-2 para partido ID {}: {}", event.getMatchId(), e.getMessage(), e);
        }
    }
}

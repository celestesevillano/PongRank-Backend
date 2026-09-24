package org.example.pongrankbackend.Tournament.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.MatchSet.MatchSet;
import org.example.pongrankbackend.Player.Player;
import org.springframework.stereotype.Component;

import java.util.Optional;

/*
 * PROVISIONAL adapter until the Match module exposes a service (pending with Adriana).
 * It only uses the existing Match / MatchSet entities: it creates Match rows in CREATED status and reads
 * matches that the Match module already marked as CONFIRMED. It does NOT validate or compute scores:
 * it only adds up the sets and points already stored. Replace it with a call to MatchService when available.
 */
@Component
public class ProvisionalMatchIntegrationAdapter implements MatchIntegrationPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Match createMatch(Player player1, Player player2, MatchFormat format) {
        Match match = Match.builder()
                .player1(player1)
                .player2(player2)
                .format(format)
                .status(MatchStatus.CREATED)
                .build();
        entityManager.persist(match);
        return match;
    }

    @Override
    public Optional<MatchOutcome> findConfirmedOutcome(Match match) {
        if (match == null || match.getStatus() != MatchStatus.CONFIRMED || match.getSets().isEmpty()) {
            return Optional.empty();
        }

        int setsPlayer1 = 0;
        int setsPlayer2 = 0;
        int pointsPlayer1 = 0;
        int pointsPlayer2 = 0;
        for (MatchSet set : match.getSets()) {
            pointsPlayer1 += set.getScorePlayer1();
            pointsPlayer2 += set.getScorePlayer2();
            if (set.getScorePlayer1() > set.getScorePlayer2()) {
                setsPlayer1++;
            } else if (set.getScorePlayer2() > set.getScorePlayer1()) {
                setsPlayer2++;
            }
        }

        if (setsPlayer1 == setsPlayer2) {
            return Optional.empty();
        }
        Long winnerId = setsPlayer1 > setsPlayer2 ? match.getPlayer1().getId() : match.getPlayer2().getId();
        return Optional.of(new MatchOutcome(winnerId, setsPlayer1, setsPlayer2, pointsPlayer1, pointsPlayer2));
    }
}

package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.Tournament.TournamentMatch;
import org.example.pongrankbackend.Tournament.TournamentMatchStatus;
import org.example.pongrankbackend.Tournament.integration.MatchIntegrationPort;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.springframework.stereotype.Component;

@Component
public class TournamentMatchScheduler {

    private final MatchIntegrationPort matchIntegrationPort;

    public TournamentMatchScheduler(MatchIntegrationPort matchIntegrationPort) {
        this.matchIntegrationPort = matchIntegrationPort;
    }

    public void schedule(Tournament tournament, TournamentMatch match) {
        if (match.getPlayer1().getId().equals(match.getPlayer2().getId())) {
            throw new ConflictException("Un jugador no puede enfrentarse a sí mismo");
        }
        // Never create a second real Match for the same tournament match
        if (match.getMatch() == null) {
            match.setMatch(matchIntegrationPort.createMatch(match.getPlayer1(), match.getPlayer2(), tournament.getMatchFormat()));
        }
        match.setStatus(TournamentMatchStatus.SCHEDULED);
    }

    public void placeInNextMatch(TournamentMatch finished, TournamentMatch next) {
        if (next == null) {
            return;
        }
        if (finished.getBracketPosition() % 2 == 0) {
            next.setPlayer1(finished.getWinner());
        } else {
            next.setPlayer2(finished.getWinner());
        }
    }
}

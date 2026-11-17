package org.example.pongrankbackend.Match.service;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.dto.LiveMatchAction;
import org.example.pongrankbackend.Match.dto.LiveMatchState;
import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.InvalidMatchStateException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
public class LiveMatchService {
    private final MatchRepository matches;

    public LiveMatchService(MatchRepository matches) {
        this.matches = matches;
    }

    @Transactional(readOnly = true)
    public LiveMatchState get(Long matchId, Long playerId) {
        Match match = matches.findById(matchId).orElseThrow(this::missing);
        participant(match, playerId);
        return snapshot(match);
    }

    @Transactional
    public LiveMatchState apply(Long matchId, Long playerId, LiveMatchAction action) {
        Match match = matches.findForLiveUpdate(matchId).orElseThrow(this::missing);
        int player = participant(match, playerId);
        if (match.getStatus() != MatchStatus.CREATED || match.getPlayer2() == null) {
            throw new InvalidMatchStateException("La partida no admite cambios en el marcador.");
        }
        if (action.action() == LiveMatchAction.Kind.READY) {
            if (player == 1 && match.getPlayer1ReadyAt() == null) match.setPlayer1ReadyAt(Instant.now());
            if (player == 2 && match.getPlayer2ReadyAt() == null) match.setPlayer2ReadyAt(Instant.now());
        } else {
            if (!Objects.equals(match.getVersion(), action.revision())) {
                throw new ConflictException("El marcador cambió. Revisa el último punto y vuelve a intentar.");
            }
            if (match.getPlayer1ReadyAt() == null || match.getPlayer2ReadyAt() == null) {
                throw new InvalidMatchStateException("Ambos jugadores deben confirmar que están listos.");
            }
            String points = match.getLivePoints() == null ? "" : match.getLivePoints();
            LiveScore score = LiveScore.from(points, match.getFormat());
            if (action.action() == LiveMatchAction.Kind.POINT) {
                if (score.finished()) throw new InvalidMatchStateException("Ya se completaron los sets de la partida.");
                match.setLivePoints(points + player);
            } else {
                if (score.lastPointBy() == null || score.lastPointBy() != player) {
                    throw new InvalidMatchStateException("Solo puedes deshacer un punto propio que sea el último registrado.");
                }
                match.setLivePoints(points.substring(0, points.length() - 1));
            }
        }
        return snapshot(matches.saveAndFlush(match));
    }

    private int participant(Match match, Long playerId) {
        if (match.getPlayer1().getId().equals(playerId)) return 1;
        if (match.getPlayer2() != null && match.getPlayer2().getId().equals(playerId)) return 2;
        throw new UnauthorizedActionException("El marcador en vivo está disponible para los participantes.");
    }

    private LiveMatchState snapshot(Match match) {
        LiveScore score = LiveScore.from(match.getLivePoints(), match.getFormat());
        return new LiveMatchState(match.getVersion(), match.getStatus(), match.getPlayer1ReadyAt() != null,
                match.getPlayer2ReadyAt() != null, score.sets(), score.player1(), score.player2(), score.lastPointBy(), score.finished());
    }

    private ResourceNotFoundException missing() {
        return new ResourceNotFoundException("No encontramos la partida.");
    }
}

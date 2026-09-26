package org.example.pongrankbackend.Match.websocket;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.dto.MatchResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MatchWebSocketNotifier {

    private static final Logger log = LoggerFactory.getLogger(MatchWebSocketNotifier.class);

    private final SimpMessagingTemplate messagingTemplate;

    public MatchWebSocketNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyMatchCreated(Match match, MatchResponseDTO dto) {
        try {
            if (match.getPlayer2() != null) {
                // Notificar al oponente desafiado directamente
                String destination = "/queue/matches/" + match.getPlayer2().getId() + "/invitation";
                messagingTemplate.convertAndSend(destination, dto);
                log.info("Notificación WebSocket de reto enviada a: {}", destination);
            } else if (match.getCommunity() != null) {
                // Notificar en el canal de la comunidad para retos abiertos
                String destination = "/topic/communities/" + match.getCommunity().getId() + "/matches";
                messagingTemplate.convertAndSend(destination, dto);
                log.info("Notificación WebSocket de reto abierto enviada a: {}", destination);
            }
        } catch (Exception e) {
            log.warn("No se pudo emitir notificación WebSocket de creación de partido: {}", e.getMessage());
        }
    }

    public void notifyMatchJoined(Long recipientPlayerId, MatchResponseDTO dto) {
        try {
            String destination = "/queue/matches/" + recipientPlayerId + "/joined";
            messagingTemplate.convertAndSend(destination, (Object) Map.of(
                    "type", "MATCH_JOINED",
                    "match", dto
            ));
            log.info("Notificación WebSocket de partido libre unido enviada a: {}", destination);
        } catch (Exception e) {
            log.warn("No se pudo emitir notificación WebSocket de partido unido: {}", e.getMessage());
        }
    }

    public void notifyScoreSubmitted(Long matchId, Long recipientPlayerId, MatchResponseDTO dto) {
        try {
            String destination = "/queue/matches/" + recipientPlayerId + "/score-proposed";
            messagingTemplate.convertAndSend(destination, (Object) Map.of(
                    "type", "SCORE_PROPOSED",
                    "matchId", matchId,
                    "match", dto
            ));
            log.info("Notificación WebSocket de marcador propuesto enviada a: {}", destination);
        } catch (Exception e) {
            log.warn("No se pudo emitir notificación WebSocket de marcador propuesto: {}", e.getMessage());
        }
    }

    public void notifyMatchConfirmed(Long matchId, MatchResponseDTO dto) {
        try {
            String destination = "/topic/matches/" + matchId;
            messagingTemplate.convertAndSend(destination, (Object) Map.of(
                    "type", "MATCH_CONFIRMED",
                    "matchId", matchId,
                    "match", dto
            ));
            log.info("Notificación WebSocket de confirmación enviada a: {}", destination);
        } catch (Exception e) {
            log.warn("No se pudo emitir notificación WebSocket de confirmación: {}", e.getMessage());
        }
    }

    public void notifyMatchDisputed(Long matchId, String reason, MatchResponseDTO dto) {
        try {
            String destination = "/topic/matches/" + matchId;
            messagingTemplate.convertAndSend(destination, (Object) Map.of(
                    "type", "MATCH_DISPUTED",
                    "matchId", matchId,
                    "reason", reason,
                    "match", dto
            ));
            log.info("Notificación WebSocket de disputa enviada a: {}", destination);
        } catch (Exception e) {
            log.warn("No se pudo emitir notificación WebSocket de disputa: {}", e.getMessage());
        }
    }
}

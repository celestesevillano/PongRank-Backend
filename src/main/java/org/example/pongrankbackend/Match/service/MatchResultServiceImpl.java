package org.example.pongrankbackend.Match.service;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.dto.*;
import org.example.pongrankbackend.Match.event.MatchConfirmedEvent;
import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.Match.websocket.MatchWebSocketNotifier;
import org.example.pongrankbackend.MatchSet.MatchSet;
import org.example.pongrankbackend.MatchSet.dto.MatchSetRequestDTO;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.InvalidMatchStateException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MatchResultServiceImpl implements MatchResultService {

    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final MatchRuleValidator matchRuleValidator;
    private final MatchWebSocketNotifier webSocketNotifier;
    private final ApplicationEventPublisher eventPublisher;
    private final MatchMapper matchMapper;

    public MatchResultServiceImpl(MatchRepository matchRepository,
                                  PlayerRepository playerRepository,
                                  MatchRuleValidator matchRuleValidator,
                                  MatchWebSocketNotifier webSocketNotifier,
                                  ApplicationEventPublisher eventPublisher,
                                  MatchMapper matchMapper) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.matchRuleValidator = matchRuleValidator;
        this.webSocketNotifier = webSocketNotifier;
        this.eventPublisher = eventPublisher;
        this.matchMapper = matchMapper;
    }

    @Override
    @Transactional
    public MatchDetailResponseDTO submitScore(Long matchId, Long submittingPlayerId, MatchScoreSubmitDTO dto) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado con ID: " + matchId));

        boolean isP1 = validateSubmittingParticipant(match, submittingPlayerId);
        validateSubmittableState(match);

        // Reglas de juego ITTF (sets a 11, ventaja de 2, deuce, formato BO3/BO5/BO7 y corte inmediato)
        matchRuleValidator.validateSetScoresAndDetermineWinner(match.getFormat(), dto.getSets());
        replaceMatchSets(match, dto.getSets());

        MatchStatus proposedStatus = isP1 ? MatchStatus.PROPOSED_P1 : MatchStatus.PROPOSED_P2;
        match.setStatus(proposedStatus);
        Match updatedMatch;
        try {
            updatedMatch = matchRepository.saveAndFlush(match);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("El partido cambió mientras enviabas el marcador; actualízalo y vuelve a intentar");
        }

        MatchDetailResponseDTO detailDTO = matchMapper.toDetailResponseDTO(updatedMatch);

        // Notificar al rival mediante WebSocket para que confirme o dispute
        Long recipientPlayerId = isP1 ? match.getPlayer2().getId() : match.getPlayer1().getId();
        webSocketNotifier.notifyScoreSubmitted(match.getId(), recipientPlayerId, matchMapper.toResponseDTO(updatedMatch));

        return detailDTO;
    }

    // Anti-trampa 1: el usuario que reporta debe ser participante del partido. Devuelve true si es player1.
    private boolean validateSubmittingParticipant(Match match, Long submittingPlayerId) {
        boolean isP1 = match.getPlayer1().getId().equals(submittingPlayerId);
        boolean isP2 = match.getPlayer2() != null && match.getPlayer2().getId().equals(submittingPlayerId);

        if (!isP1 && !isP2) {
            throw new UnauthorizedActionException("Solo un jugador participante en el partido puede reportar el marcador");
        }
        return isP1;
    }

    // Anti-trampa 2: validación de estado del partido
    private void validateSubmittableState(Match match) {
        if (match.getStatus() == MatchStatus.CONFIRMED) {
            throw new InvalidMatchStateException("El partido ya fue confirmado y cerrado. No se puede modificar el marcador.");
        }
        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw new InvalidMatchStateException("El partido está cancelado. No se puede reportar marcador.");
        }
        if (match.getStatus() == MatchStatus.WALKOVER) {
            throw new InvalidMatchStateException("El partido fue cerrado por incomparecencia (W.O.). No se puede reportar marcador.");
        }
    }

    // Limpia los sets previos si existían y persiste los nuevos sets validados sobre la colección existente
    private void replaceMatchSets(Match match, List<MatchSetRequestDTO> setDtos) {
        if (match.getSets() == null) {
            match.setSets(new ArrayList<>());
        } else {
            match.getSets().clear();
        }

        for (MatchSetRequestDTO setDto : setDtos) {
            MatchSet matchSet = MatchSet.builder()
                    .match(match)
                    .setNumber(setDto.getSetNumber())
                    .scorePlayer1(setDto.getScorePlayer1())
                    .scorePlayer2(setDto.getScorePlayer2())
                    .build();
            match.getSets().add(matchSet);
        }
    }

    @Override
    @Transactional
    public MatchDetailResponseDTO confirmMatch(Long matchId, Long actingPlayerId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado con ID: " + matchId));

        validateConfirmableState(match);
        validateNotSelfConfirming(match, actingPlayerId);

        boolean p1Won = didPlayer1WinBySets(match);

        // Sellar partido como CONFIRMED
        match.setStatus(MatchStatus.CONFIRMED);
        match.setWinner(p1Won ? match.getPlayer1() : match.getPlayer2());
        match.setConfirmedAt(LocalDateTime.now());
        Match savedMatch = matchRepository.save(match);

        // Disparar evento asíncrono para recálculo de rating Glicko-2 sin retrasar la respuesta REST
        eventPublisher.publishEvent(new MatchConfirmedEvent(this, savedMatch.getId(), p1Won));

        MatchDetailResponseDTO detailDTO = matchMapper.toDetailResponseDTO(savedMatch);

        // Notificar resolución vía WebSocket a ambos jugadores
        webSocketNotifier.notifyMatchConfirmed(savedMatch.getId(), matchMapper.toResponseDTO(savedMatch));

        return detailDTO;
    }

    // Mecanismo anti-trampa: doble confirmación estricta
    private void validateConfirmableState(Match match) {
        if (match.getStatus() == MatchStatus.CREATED) {
            throw new InvalidMatchStateException("No se puede confirmar un partido que aún no tiene marcador reportado");
        }
        if (match.getStatus() == MatchStatus.CONFIRMED) {
            throw new InvalidMatchStateException("El partido ya fue confirmado y cerrado previamente");
        }
        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw new InvalidMatchStateException("El partido se encuentra cancelado");
        }
        if (match.getStatus() == MatchStatus.WALKOVER) {
            throw new InvalidMatchStateException("El partido fue cerrado por incomparecencia (W.O.)");
        }
    }

    // Anti-auto-aprobación: el proponente NO puede auto-confirmarse su marcador
    private void validateNotSelfConfirming(Match match, Long actingPlayerId) {
        if (match.getStatus() == MatchStatus.PROPOSED_P1) {
            if (actingPlayerId.equals(match.getPlayer1().getId())) {
                throw new UnauthorizedActionException("Mecanismo anti-trampa: El jugador proponente no puede confirmar su propio resultado. Debe confirmarlo el contrincante.");
            }
            if (!actingPlayerId.equals(match.getPlayer2().getId())) {
                throw new UnauthorizedActionException("Solo el jugador contrincante puede confirmar el resultado propuesto");
            }
        } else if (match.getStatus() == MatchStatus.PROPOSED_P2) {
            if (actingPlayerId.equals(match.getPlayer2().getId())) {
                throw new UnauthorizedActionException("Mecanismo anti-trampa: El jugador proponente no puede confirmar su propio resultado. Debe confirmarlo el contrincante.");
            }
            if (!actingPlayerId.equals(match.getPlayer1().getId())) {
                throw new UnauthorizedActionException("Solo el jugador contrincante puede confirmar el resultado propuesto");
            }
        }
    }

    private boolean didPlayer1WinBySets(Match match) {
        long setsWonP1 = match.getSets().stream()
                .filter(s -> s.getScorePlayer1() > s.getScorePlayer2())
                .count();
        long setsWonP2 = match.getSets().stream()
                .filter(s -> s.getScorePlayer2() > s.getScorePlayer1())
                .count();
        return setsWonP1 > setsWonP2;
    }

    @Override
    @Transactional
    public MatchDetailResponseDTO disputeMatch(Long matchId, Long actingPlayerId, MatchDisputeRequestDTO dto) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado con ID: " + matchId));

        if (match.getStatus() != MatchStatus.PROPOSED_P1 && match.getStatus() != MatchStatus.PROPOSED_P2) {
            throw new InvalidMatchStateException("Solo se puede disputar un partido con marcador propuesto pendiente de confirmación");
        }

        // Anti-trampa: Solo el rival receptor del marcador puede objetar
        if (match.getStatus() == MatchStatus.PROPOSED_P1 && !actingPlayerId.equals(match.getPlayer2().getId())) {
            throw new UnauthorizedActionException("Solo el contrincante receptor puede disputar el marcador propuesto");
        }
        if (match.getStatus() == MatchStatus.PROPOSED_P2 && !actingPlayerId.equals(match.getPlayer1().getId())) {
            throw new UnauthorizedActionException("Solo el contrincante receptor puede disputar el marcador propuesto");
        }

        match.setStatus(MatchStatus.DISPUTED);
        match.setDisputeReason(dto.getReason());
        Match savedMatch = matchRepository.save(match);

        MatchDetailResponseDTO detailDTO = matchMapper.toDetailResponseDTO(savedMatch);

        // Notificar en tiempo real la disputa
        webSocketNotifier.notifyMatchDisputed(savedMatch.getId(), dto.getReason(), matchMapper.toResponseDTO(savedMatch));

        return detailDTO;
    }

    @Override
    @Transactional
    public MatchDetailResponseDTO resolveDispute(Long matchId, MatchDisputeResolutionDTO dto) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado con ID: " + matchId));

        if (match.getStatus() != MatchStatus.DISPUTED) {
            throw new InvalidMatchStateException("Solo se puede resolver un partido que está en disputa");
        }

        match.setDisputeReason(match.getDisputeReason() + " | Resolución del administrador: " + dto.getResolutionNote());

        if (dto.getWinnerId() == null) {
            match.setStatus(MatchStatus.CANCELLED);
            Match savedMatch = matchRepository.save(match);
            return matchMapper.toDetailResponseDTO(savedMatch);
        }

        boolean isP1 = match.getPlayer1().getId().equals(dto.getWinnerId());
        boolean isP2 = match.getPlayer2() != null && match.getPlayer2().getId().equals(dto.getWinnerId());
        if (!isP1 && !isP2) {
            throw new InvalidMatchStateException("El jugador ganador indicado no participa en este partido");
        }

        match.setStatus(MatchStatus.CONFIRMED);
        match.setWinner(isP1 ? match.getPlayer1() : match.getPlayer2());
        match.setConfirmedAt(LocalDateTime.now());
        Match savedMatch = matchRepository.save(match);

        eventPublisher.publishEvent(new MatchConfirmedEvent(this, savedMatch.getId(), isP1));

        MatchDetailResponseDTO detailDTO = matchMapper.toDetailResponseDTO(savedMatch);
        webSocketNotifier.notifyMatchConfirmed(savedMatch.getId(), matchMapper.toResponseDTO(savedMatch));

        return detailDTO;
    }

    @Override
    @Transactional
    public MatchResponseDTO closeMatchAsWalkover(Long matchId, Long winnerId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado con ID: " + matchId));

        if (match.getStatus() == MatchStatus.CONFIRMED) {
            throw new InvalidMatchStateException("El partido ya fue confirmado y cerrado previamente");
        }
        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw new InvalidMatchStateException("El partido se encuentra cancelado");
        }
        if (match.getStatus() == MatchStatus.WALKOVER) {
            throw new InvalidMatchStateException("El partido ya fue cerrado por W.O.");
        }

        Player winner = null;
        if (winnerId != null) {
            boolean isP1 = match.getPlayer1().getId().equals(winnerId);
            boolean isP2 = match.getPlayer2() != null && match.getPlayer2().getId().equals(winnerId);
            if (!isP1 && !isP2) {
                throw new InvalidMatchStateException("El jugador ganador indicado no participa en este partido");
            }
            winner = playerRepository.findById(winnerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Jugador ganador no encontrado con ID: " + winnerId));
        }

        match.setStatus(MatchStatus.WALKOVER);
        match.setWinner(winner);
        match.setConfirmedAt(LocalDateTime.now());

        Match savedMatch = matchRepository.save(match);
        MatchResponseDTO responseDTO = matchMapper.toResponseDTO(savedMatch);

        webSocketNotifier.notifyMatchConfirmed(savedMatch.getId(), responseDTO);

        return responseDTO;
    }
}

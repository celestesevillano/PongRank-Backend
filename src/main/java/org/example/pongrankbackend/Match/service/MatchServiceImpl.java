package org.example.pongrankbackend.Match.service;

import jakarta.persistence.EntityManager;
import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Friendship.repository.FriendshipRepository;
import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.dto.*;
import org.example.pongrankbackend.Match.event.MatchConfirmedEvent;
import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.Match.websocket.MatchWebSocketNotifier;
import org.example.pongrankbackend.MatchSet.MatchSet;
import org.example.pongrankbackend.MatchSet.dto.MatchSetRequestDTO;
import org.example.pongrankbackend.MatchSet.dto.MatchSetResponseDTO;
import org.example.pongrankbackend.MatchSet.repository.MatchSetRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.InvalidMatchStateException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final MatchSetRepository matchSetRepository;
    private final PlayerRepository playerRepository;
    private final FriendshipRepository friendshipRepository;
    private final MatchRuleValidator matchRuleValidator;
    private final MatchWebSocketNotifier webSocketNotifier;
    private final ApplicationEventPublisher eventPublisher;
    private final ModelMapper modelMapper;
    private final EntityManager entityManager;

    public MatchServiceImpl(MatchRepository matchRepository,
                            MatchSetRepository matchSetRepository,
                            PlayerRepository playerRepository,
                            FriendshipRepository friendshipRepository,
                            MatchRuleValidator matchRuleValidator,
                            MatchWebSocketNotifier webSocketNotifier,
                            ApplicationEventPublisher eventPublisher,
                            ModelMapper modelMapper,
                            EntityManager entityManager) {
        this.matchRepository = matchRepository;
        this.matchSetRepository = matchSetRepository;
        this.playerRepository = playerRepository;
        this.friendshipRepository = friendshipRepository;
        this.matchRuleValidator = matchRuleValidator;
        this.webSocketNotifier = webSocketNotifier;
        this.eventPublisher = eventPublisher;
        this.modelMapper = modelMapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public MatchResponseDTO createMatch(Long creatorPlayerId, MatchCreateRequestDTO dto) {
        Player creator = playerRepository.findById(creatorPlayerId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador creador no encontrado con ID: " + creatorPlayerId));

        Player opponent = null;
        if (dto.getOpponentId() != null) {
            opponent = playerRepository.findById(dto.getOpponentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Jugador oponente no encontrado con ID: " + dto.getOpponentId()));
        }

        // Obtener relación de amistad si existe para validar la modalidad FRIEND
        Optional<Friendship> friendshipOpt = Optional.empty();
        if (opponent != null) {
            friendshipOpt = friendshipRepository.findFriendshipBetween(creator.getId(), opponent.getId());
        }

        // Validación estricta de las 3 opciones (FRIEND, COMMUNITY, LOCATION)
        matchRuleValidator.validateMatchCreation(creator, opponent, dto, friendshipOpt);

        Community community = null;
        if (dto.getCommunityId() != null) {
            community = entityManager.find(Community.class, dto.getCommunityId());
            if (community == null) {
                throw new ResourceNotFoundException("Comunidad no encontrada con ID: " + dto.getCommunityId());
            }
        }

        Match match = Match.builder()
                .player1(creator)
                .player2(opponent)
                .community(community)
                .format(dto.getFormat())
                .matchType(dto.getMatchType())
                .status(MatchStatus.CREATED)
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .build();

        Match savedMatch = matchRepository.save(match);
        MatchResponseDTO responseDTO = toResponseDTO(savedMatch);

        // Notificar en tiempo real vía WebSockets
        webSocketNotifier.notifyMatchCreated(savedMatch, responseDTO);

        return responseDTO;
    }

    @Override
    public MatchDetailResponseDTO getMatchById(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado con ID: " + matchId));
        return toDetailResponseDTO(match);
    }

    @Override
    public List<MatchResponseDTO> getMatchesByPlayer(Long playerId) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }
        return matchRepository.findAllByPlayerId(playerId).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    public List<MatchResponseDTO> getMatchesByPlayerAndStatus(Long playerId, MatchStatus status) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }
        return matchRepository.findAllByPlayerIdAndStatus(playerId, status).stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    public Page<MatchResponseDTO> getMatchesByPlayer(Long playerId, Pageable pageable) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }
        return matchRepository.findAllByPlayerId(playerId, pageable)
                .map(this::toResponseDTO);
    }

    @Override
    public Page<MatchResponseDTO> getMatchesByPlayerAndStatus(Long playerId, MatchStatus status, Pageable pageable) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }
        return matchRepository.findAllByPlayerIdAndStatus(playerId, status, pageable)
                .map(this::toResponseDTO);
    }

    @Override
    public Page<MatchResponseDTO> getAllMatches(Pageable pageable) {
        return matchRepository.findAll(pageable)
                .map(this::toResponseDTO);
    }

    @Override
    @Transactional
    public MatchDetailResponseDTO submitScore(Long matchId, Long submittingPlayerId, MatchScoreSubmitDTO dto) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado con ID: " + matchId));

        // Anti-trampa 1: El usuario que reporta debe ser participante del partido
        boolean isP1 = match.getPlayer1().getId().equals(submittingPlayerId);
        boolean isP2 = match.getPlayer2() != null && match.getPlayer2().getId().equals(submittingPlayerId);

        if (!isP1 && !isP2) {
            throw new UnauthorizedActionException("Solo un jugador participante en el partido puede reportar el marcador");
        }

        // Anti-trampa 2: Validación de estado del partido
        if (match.getStatus() == MatchStatus.CONFIRMED) {
            throw new InvalidMatchStateException("El partido ya fue confirmado y cerrado. No se puede modificar el marcador.");
        }
        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw new InvalidMatchStateException("El partido está cancelado. No se puede reportar marcador.");
        }

        // Anti-trampa 3: Reglas de juego ITTF (sets a 11, ventaja de 2, deuce, formato BO3/BO5/BO7 y corte inmediato)
        MatchRuleValidator.MatchValidationResult validationResult =
                matchRuleValidator.validateSetScoresAndDetermineWinner(match.getFormat(), dto.getSets());

        // Limpiar sets previos si existían y persistir los nuevos sets validados
        if (match.getSets() != null && !match.getSets().isEmpty()) {
            matchSetRepository.deleteByMatchId(match.getId());
            match.getSets().clear();
        }

        List<MatchSet> newSets = new ArrayList<>();
        for (MatchSetRequestDTO setDto : dto.getSets()) {
            MatchSet matchSet = MatchSet.builder()
                    .match(match)
                    .setNumber(setDto.getSetNumber())
                    .scorePlayer1(setDto.getScorePlayer1())
                    .scorePlayer2(setDto.getScorePlayer2())
                    .build();
            newSets.add(matchSetRepository.save(matchSet));
        }
        match.setSets(newSets);

        // Actualizar estado según quién propuso el marcador
        MatchStatus proposedStatus = isP1 ? MatchStatus.PROPOSED_P1 : MatchStatus.PROPOSED_P2;
        match.setStatus(proposedStatus);
        Match updatedMatch = matchRepository.save(match);

        MatchDetailResponseDTO detailDTO = toDetailResponseDTO(updatedMatch);

        // Notificar al rival mediante WebSocket para que confirme o dispute
        Long recipientPlayerId = isP1 ? match.getPlayer2().getId() : match.getPlayer1().getId();
        webSocketNotifier.notifyScoreSubmitted(match.getId(), recipientPlayerId, toResponseDTO(updatedMatch));

        return detailDTO;
    }

    @Override
    @Transactional
    public MatchDetailResponseDTO confirmMatch(Long matchId, Long actingPlayerId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado con ID: " + matchId));

        // Mecanismo Anti-Trampa: Doble confirmación estricta
        if (match.getStatus() == MatchStatus.CREATED) {
            throw new InvalidMatchStateException("No se puede confirmar un partido que aún no tiene marcador reportado");
        }
        if (match.getStatus() == MatchStatus.CONFIRMED) {
            throw new InvalidMatchStateException("El partido ya fue confirmado y cerrado previamente");
        }
        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw new InvalidMatchStateException("El partido se encuentra cancelado");
        }

        // Anti-auto-aprobación: El proponente NO puede auto-confirmarse su marcador
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

        // Sellar partido como CONFIRMED
        match.setStatus(MatchStatus.CONFIRMED);
        match.setConfirmedAt(LocalDateTime.now());
        Match savedMatch = matchRepository.save(match);

        // Determinar ganador para el cálculo Glicko-2
        long setsWonP1 = savedMatch.getSets().stream()
                .filter(s -> s.getScorePlayer1() > s.getScorePlayer2())
                .count();
        long setsWonP2 = savedMatch.getSets().stream()
                .filter(s -> s.getScorePlayer2() > s.getScorePlayer1())
                .count();
        boolean p1Won = setsWonP1 > setsWonP2;

        // Disparar evento asíncrono para recálculo de rating Glicko-2 sin retrasar la respuesta REST
        eventPublisher.publishEvent(new MatchConfirmedEvent(this, savedMatch.getId(), p1Won));

        MatchDetailResponseDTO detailDTO = toDetailResponseDTO(savedMatch);

        // Notificar resolución vía WebSocket a ambos jugadores
        webSocketNotifier.notifyMatchConfirmed(savedMatch.getId(), toResponseDTO(savedMatch));

        return detailDTO;
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

        MatchDetailResponseDTO detailDTO = toDetailResponseDTO(savedMatch);

        // Notificar en tiempo real la disputa
        webSocketNotifier.notifyMatchDisputed(savedMatch.getId(), dto.getReason(), toResponseDTO(savedMatch));

        return detailDTO;
    }

    @Override
    @Transactional
    public MatchResponseDTO cancelMatch(Long matchId, Long actingPlayerId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado con ID: " + matchId));

        boolean isP1 = match.getPlayer1().getId().equals(actingPlayerId);
        boolean isP2 = match.getPlayer2() != null && match.getPlayer2().getId().equals(actingPlayerId);

        if (!isP1 && !isP2) {
            throw new UnauthorizedActionException("Solo un jugador participante puede cancelar el partido");
        }

        if (match.getStatus() == MatchStatus.CONFIRMED) {
            throw new InvalidMatchStateException("No se puede cancelar un partido que ya ha sido confirmado");
        }

        match.setStatus(MatchStatus.CANCELLED);
        Match savedMatch = matchRepository.save(match);
        return toResponseDTO(savedMatch);
    }

    private MatchResponseDTO toResponseDTO(Match match) {
        PlayerSummaryDTO p1 = modelMapper.map(match.getPlayer1(), PlayerSummaryDTO.class);
        PlayerSummaryDTO p2 = match.getPlayer2() != null ? modelMapper.map(match.getPlayer2(), PlayerSummaryDTO.class) : null;

        Long commId = match.getCommunity() != null ? match.getCommunity().getId() : null;
        String commName = match.getCommunity() != null ? match.getCommunity().getName() : null;

        String scoreSummary = null;
        Long winnerId = null;

        if (match.getSets() != null && !match.getSets().isEmpty()) {
            long w1 = match.getSets().stream().filter(s -> s.getScorePlayer1() > s.getScorePlayer2()).count();
            long w2 = match.getSets().stream().filter(s -> s.getScorePlayer2() > s.getScorePlayer1()).count();
            scoreSummary = w1 + " - " + w2;
            if (match.getStatus() == MatchStatus.CONFIRMED && match.getPlayer2() != null) {
                winnerId = w1 > w2 ? match.getPlayer1().getId() : match.getPlayer2().getId();
            }
        }

        return MatchResponseDTO.builder()
                .id(match.getId())
                .player1(p1)
                .player2(p2)
                .communityId(commId)
                .communityName(commName)
                .format(match.getFormat())
                .matchType(match.getMatchType())
                .status(match.getStatus())
                .latitude(match.getLatitude())
                .longitude(match.getLongitude())
                .scoreSummary(scoreSummary)
                .winnerId(winnerId)
                .ratingDeltaP1(match.getRatingDeltaP1())
                .ratingDeltaP2(match.getRatingDeltaP2())
                .createdAt(match.getCreatedAt())
                .confirmedAt(match.getConfirmedAt())
                .build();
    }

    private MatchDetailResponseDTO toDetailResponseDTO(Match match) {
        MatchResponseDTO base = toResponseDTO(match);

        List<MatchSetResponseDTO> setDTOs = new ArrayList<>();
        if (match.getSets() != null) {
            setDTOs = match.getSets().stream()
                    .map(s -> MatchSetResponseDTO.builder()
                            .id(s.getId())
                            .setNumber(s.getSetNumber())
                            .scorePlayer1(s.getScorePlayer1())
                            .scorePlayer2(s.getScorePlayer2())
                            .winnerPlayerNumber(s.getScorePlayer1() > s.getScorePlayer2() ? 1 : 2)
                            .build())
                    .toList();
        }

        return MatchDetailResponseDTO.builder()
                .id(base.getId())
                .player1(base.getPlayer1())
                .player2(base.getPlayer2())
                .communityId(base.getCommunityId())
                .communityName(base.getCommunityName())
                .format(base.getFormat())
                .matchType(base.getMatchType())
                .status(base.getStatus())
                .latitude(base.getLatitude())
                .longitude(base.getLongitude())
                .scoreSummary(base.getScoreSummary())
                .winnerId(base.getWinnerId())
                .ratingDeltaP1(base.getRatingDeltaP1())
                .ratingDeltaP2(base.getRatingDeltaP2())
                .disputeReason(match.getDisputeReason())
                .createdAt(base.getCreatedAt())
                .confirmedAt(base.getConfirmedAt())
                .sets(setDTOs)
                .build();
    }
}

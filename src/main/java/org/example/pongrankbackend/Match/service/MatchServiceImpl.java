package org.example.pongrankbackend.Match.service;

import jakarta.persistence.EntityManager;
import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Friendship.repository.FriendshipRepository;
import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.MatchType;
import org.example.pongrankbackend.Match.dto.*;
import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.Match.websocket.MatchWebSocketNotifier;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.common.exception.InvalidMatchStateException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final FriendshipRepository friendshipRepository;
    private final MatchRuleValidator matchRuleValidator;
    private final MatchWebSocketNotifier webSocketNotifier;
    private final MatchMapper matchMapper;
    private final EntityManager entityManager;
    private final OpenMatchService openMatchService;
    private final MatchResultService matchResultService;

    public MatchServiceImpl(MatchRepository matchRepository,
                            PlayerRepository playerRepository,
                            FriendshipRepository friendshipRepository,
                            MatchRuleValidator matchRuleValidator,
                            MatchWebSocketNotifier webSocketNotifier,
                            MatchMapper matchMapper,
                            EntityManager entityManager,
                            OpenMatchService openMatchService,
                            MatchResultService matchResultService) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.friendshipRepository = friendshipRepository;
        this.matchRuleValidator = matchRuleValidator;
        this.webSocketNotifier = webSocketNotifier;
        this.matchMapper = matchMapper;
        this.entityManager = entityManager;
        this.openMatchService = openMatchService;
        this.matchResultService = matchResultService;
    }

    @Override
    @Transactional
    public MatchResponseDTO createMatch(Long creatorPlayerId, MatchCreateRequestDTO dto) {
        Player creator = playerRepository.findById(creatorPlayerId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador creador no encontrado con ID: " + creatorPlayerId));
        Player opponent = findOpponentIfPresent(dto.getOpponentId());

        // Obtener relación de amistad si existe para validar la modalidad FRIEND
        Optional<Friendship> friendshipOpt = opponent == null
                ? Optional.empty()
                : friendshipRepository.findFriendshipBetween(creator.getId(), opponent.getId());

        // Validación estricta de las 3 opciones (FRIEND, COMMUNITY, LOCATION)
        matchRuleValidator.validateMatchCreation(creator, opponent, dto, friendshipOpt);

        Community community = findCommunityIfPresent(dto.getCommunityId());
        Tournament tournament = findTournamentIfPresent(dto.getTournamentId());

        Match match = Match.builder()
                .player1(creator)
                .player2(opponent)
                .community(community)
                .tournament(tournament)
                .format(dto.getFormat())
                .matchType(dto.getMatchType())
                .status(MatchStatus.CREATED)
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .build();

        Match savedMatch = matchRepository.save(match);
        MatchResponseDTO responseDTO = matchMapper.toResponseDTO(savedMatch);

        // Notificar en tiempo real vía WebSockets
        webSocketNotifier.notifyMatchCreated(savedMatch, responseDTO);

        return responseDTO;
    }

    private Player findOpponentIfPresent(Long opponentId) {
        if (opponentId == null) {
            return null;
        }
        return playerRepository.findById(opponentId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador oponente no encontrado con ID: " + opponentId));
    }

    private Community findCommunityIfPresent(Long communityId) {
        if (communityId == null) {
            return null;
        }
        Community community = entityManager.find(Community.class, communityId);
        if (community == null) {
            throw new ResourceNotFoundException("Comunidad no encontrada con ID: " + communityId);
        }
        return community;
    }

    private Tournament findTournamentIfPresent(Long tournamentId) {
        if (tournamentId == null) {
            return null;
        }
        Tournament tournament = entityManager.find(Tournament.class, tournamentId);
        if (tournament == null) {
            throw new ResourceNotFoundException("Torneo no encontrado con ID: " + tournamentId);
        }
        return tournament;
    }

    @Override
    @Transactional
    public Match createTournamentMatch(Player player1, Player player2, Tournament tournament, MatchFormat format) {
        if (player1 == null || player2 == null) {
            throw new IllegalArgumentException("Ambos jugadores son obligatorios para crear una partida de torneo");
        }
        if (player1.getId().equals(player2.getId())) {
            throw new InvalidMatchStateException("Un jugador no puede crear un partido contra sí mismo");
        }

        Match match = Match.builder()
                .player1(player1)
                .player2(player2)
                .tournament(tournament)
                .format(format != null ? format : MatchFormat.BO3)
                .matchType(MatchType.TOURNAMENT)
                .status(MatchStatus.CREATED)
                .build();

        Match savedMatch = matchRepository.save(match);
        webSocketNotifier.notifyMatchCreated(savedMatch, matchMapper.toResponseDTO(savedMatch));
        return savedMatch;
    }

    @Override
    @Transactional
    public Match createTournamentMatch(Player player1, Player player2, MatchFormat format) {
        return createTournamentMatch(player1, player2, null, format);
    }

    @Override
    public MatchDetailResponseDTO getMatchById(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado con ID: " + matchId));
        return matchMapper.toDetailResponseDTO(match);
    }

    @Override
    public List<MatchResponseDTO> getMatchesByPlayer(Long playerId) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }
        return matchRepository.findAllByPlayerId(playerId).stream()
                .map(matchMapper::toResponseDTO)
                .toList();
    }

    @Override
    public List<MatchResponseDTO> getMatchesByPlayerAndStatus(Long playerId, MatchStatus status) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }
        return matchRepository.findAllByPlayerIdAndStatus(playerId, status).stream()
                .map(matchMapper::toResponseDTO)
                .toList();
    }

    @Override
    public Page<MatchResponseDTO> getMatchesByPlayer(Long playerId, Pageable pageable) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }
        return matchRepository.findAllByPlayerId(playerId, pageable)
                .map(matchMapper::toResponseDTO);
    }

    @Override
    public Page<MatchResponseDTO> getMatchesByPlayerAndStatus(Long playerId, MatchStatus status, Pageable pageable) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }
        return matchRepository.findAllByPlayerIdAndStatus(playerId, status, pageable)
                .map(matchMapper::toResponseDTO);
    }

    @Override
    public Page<MatchResponseDTO> getAllMatches(Pageable pageable) {
        return matchRepository.findAll(pageable)
                .map(matchMapper::toResponseDTO);
    }

    @Override
    public Page<MatchResponseDTO> getMatchesByTournament(Long tournamentId, Pageable pageable) {
        Tournament tournament = entityManager.find(Tournament.class, tournamentId);
        if (tournament == null) {
            throw new ResourceNotFoundException("Torneo no encontrado con ID: " + tournamentId);
        }
        return matchRepository.findByTournamentId(tournamentId, pageable)
                .map(matchMapper::toResponseDTO);
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
        if (match.getStatus() == MatchStatus.WALKOVER) {
            throw new InvalidMatchStateException("No se puede cancelar un partido cerrado por incomparecencia (W.O.)");
        }

        match.setStatus(MatchStatus.CANCELLED);
        Match savedMatch = matchRepository.save(match);
        return matchMapper.toResponseDTO(savedMatch);
    }

    @Override
    public List<MatchResponseDTO> getOpenLocationMatchesNearby(Long requesterId, BigDecimal latitude, BigDecimal longitude) {
        return openMatchService.getOpenLocationMatchesNearby(requesterId, latitude, longitude);
    }

    @Override
    @Transactional
    public MatchResponseDTO joinOpenMatch(Long joinerId, Long matchId, MatchJoinRequestDTO dto) {
        return openMatchService.joinOpenMatch(joinerId, matchId, dto);
    }

    @Override
    @Transactional
    public MatchDetailResponseDTO submitScore(Long matchId, Long submittingPlayerId, MatchScoreSubmitDTO dto) {
        return matchResultService.submitScore(matchId, submittingPlayerId, dto);
    }

    @Override
    @Transactional
    public MatchDetailResponseDTO confirmMatch(Long matchId, Long actingPlayerId) {
        return matchResultService.confirmMatch(matchId, actingPlayerId);
    }

    @Override
    @Transactional
    public MatchDetailResponseDTO disputeMatch(Long matchId, Long actingPlayerId, MatchDisputeRequestDTO dto) {
        return matchResultService.disputeMatch(matchId, actingPlayerId, dto);
    }

    @Override
    @Transactional
    public MatchDetailResponseDTO resolveDispute(Long matchId, MatchDisputeResolutionDTO dto) {
        return matchResultService.resolveDispute(matchId, dto);
    }

    @Override
    @Transactional
    public MatchResponseDTO closeMatchAsWalkover(Long matchId, Long winnerId) {
        return matchResultService.closeMatchAsWalkover(matchId, winnerId);
    }
}

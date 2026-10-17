package org.example.pongrankbackend.Match.service;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.MatchType;
import org.example.pongrankbackend.Match.dto.*;
import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.Match.websocket.MatchWebSocketNotifier;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.InvalidMatchStateException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OpenMatchServiceImpl implements OpenMatchService {

    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final MatchRuleValidator matchRuleValidator;
    private final MatchWebSocketNotifier webSocketNotifier;
    private final MatchMapper matchMapper;

    public OpenMatchServiceImpl(MatchRepository matchRepository,
                                PlayerRepository playerRepository,
                                MatchRuleValidator matchRuleValidator,
                                MatchWebSocketNotifier webSocketNotifier,
                                MatchMapper matchMapper) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.matchRuleValidator = matchRuleValidator;
        this.webSocketNotifier = webSocketNotifier;
        this.matchMapper = matchMapper;
    }

    @Override
    public List<MatchResponseDTO> getOpenLocationMatchesNearby(Long requesterId, BigDecimal latitude, BigDecimal longitude) {
        Player requester = playerRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + requesterId));

        return matchRepository.findOpenChallenges(MatchStatus.CREATED, MatchType.LOCATION).stream()
                .filter(match -> !match.getPlayer1().getId().equals(requesterId))
                .filter(match -> withinRatingGap(requester, match.getPlayer1()))
                .filter(match -> withinDistance(match, latitude, longitude))
                .map(matchMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    public MatchResponseDTO joinOpenMatch(Long joinerId, Long matchId, MatchJoinRequestDTO dto) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado con ID: " + matchId));

        if (match.getMatchType() != MatchType.LOCATION) {
            throw new InvalidMatchStateException("Solo se puede unir a partidos libres (LOCATION) abiertos");
        }
        if (match.getPlayer2() != null || match.getStatus() != MatchStatus.CREATED) {
            throw new ConflictException("Este partido libre ya no está disponible para unirse");
        }
        if (match.getPlayer1().getId().equals(joinerId)) {
            throw new InvalidMatchStateException("No puedes unirte a tu propio partido libre");
        }

        Player joiner = playerRepository.findById(joinerId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + joinerId));

        matchRuleValidator.validateRatingGap(match.getPlayer1(), joiner);
        double distance = matchRuleValidator.calculateDistanceKm(
                match.getLatitude().doubleValue(), match.getLongitude().doubleValue(),
                dto.getLatitude().doubleValue(), dto.getLongitude().doubleValue());
        if (distance > MatchRuleValidator.MAX_LOCATION_DISTANCE_KM) {
            throw new InvalidMatchStateException(
                    String.format("La distancia con el creador del partido (%.2f km) excede el radio máximo de cercanía permitido (%.1f km)",
                            distance, MatchRuleValidator.MAX_LOCATION_DISTANCE_KM));
        }

        match.setPlayer2(joiner);
        Match savedMatch;
        try {
            savedMatch = matchRepository.saveAndFlush(match);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("Este partido libre ya fue tomado por otro jugador, intenta con otro");
        }
        MatchResponseDTO responseDTO = matchMapper.toResponseDTO(savedMatch);

        webSocketNotifier.notifyMatchJoined(match.getPlayer1().getId(), responseDTO);

        return responseDTO;
    }

    private boolean withinRatingGap(Player a, Player b) {
        return Math.abs(a.getRatingGlicko() - b.getRatingGlicko()) <= MatchRuleValidator.MAX_LOCATION_RATING_GAP;
    }

    private boolean withinDistance(Match match, BigDecimal latitude, BigDecimal longitude) {
        if (match.getLatitude() == null || match.getLongitude() == null) {
            return false;
        }
        double distance = matchRuleValidator.calculateDistanceKm(
                match.getLatitude().doubleValue(), match.getLongitude().doubleValue(),
                latitude.doubleValue(), longitude.doubleValue());
        return distance <= MatchRuleValidator.MAX_LOCATION_DISTANCE_KM;
    }
}

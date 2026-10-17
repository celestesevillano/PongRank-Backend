package org.example.pongrankbackend.TrainingSession.service;

import org.example.pongrankbackend.Membership.service.MembershipService;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.security.PlayerAccessGuard;
import org.example.pongrankbackend.TrainingSession.TrainingSession;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionCreateDTO;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionResponseDTO;
import org.example.pongrankbackend.TrainingSession.repository.TrainingSessionRepository;
import org.example.pongrankbackend.common.exception.PlanRestrictionException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TrainingSessionServiceImpl implements TrainingSessionService {

    private final TrainingSessionRepository trainingSessionRepository;
    private final PlayerRepository playerRepository;
    private final MembershipService membershipService;
    private final ModelMapper modelMapper;
    private final PlayerAccessGuard playerAccessGuard;

    public TrainingSessionServiceImpl(TrainingSessionRepository trainingSessionRepository,
                                       PlayerRepository playerRepository,
                                       MembershipService membershipService,
                                       ModelMapper modelMapper,
                                       PlayerAccessGuard playerAccessGuard) {
        this.trainingSessionRepository = trainingSessionRepository;
        this.playerRepository = playerRepository;
        this.membershipService = membershipService;
        this.modelMapper = modelMapper;
        this.playerAccessGuard = playerAccessGuard;
    }

    @Override
    @Transactional
    public TrainingSessionResponseDTO registerSession(Long requestedPlayerId, Long actingPlayerId, TrainingSessionCreateDTO dto) {
        if (requestedPlayerId != null) {
            playerAccessGuard.requireSelfOrAdmin(requestedPlayerId,
                    "No puedes registrar sesiones de entrenamiento para otro jugador");
        }
        return registerSession(actingPlayerId, dto);
    }

    @Override
    @Transactional
    public TrainingSessionResponseDTO registerSession(Long playerId, TrainingSessionCreateDTO dto) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId));

        if (!membershipService.hasCoachAccess(playerId)) {
            throw new PlanRestrictionException("Solo los jugadores con plan PRO o ENTERPRISE pueden usar el Coach");
        }

        Double previousBest = trainingSessionRepository.findTopByPlayerIdOrderByPostureScoreDesc(playerId)
                .map(TrainingSession::getPostureScore)
                .orElse(null);

        TrainingSession session = modelMapper.map(dto, TrainingSession.class);
        session.setPlayer(player);

        TrainingSession savedSession = trainingSessionRepository.save(session);

        boolean isNewBest = previousBest == null || savedSession.getPostureScore() > previousBest;

        TrainingSessionResponseDTO response = modelMapper.map(savedSession, TrainingSessionResponseDTO.class);
        response.setNewPersonalBest(isNewBest);
        return response;
    }

    @Override
    public List<TrainingSessionResponseDTO> getHistoryByPlayer(Long playerId) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }

        List<TrainingSession> sessions = trainingSessionRepository.findByPlayerIdOrderByCreatedAtDesc(playerId);
        return sessions.stream()
                .map(session -> modelMapper.map(session, TrainingSessionResponseDTO.class))
                .toList();
    }

    @Override
    public Page<TrainingSessionResponseDTO> getHistoryByPlayer(Long playerId, Pageable pageable) {
        playerAccessGuard.requireSelfOrAdmin(playerId, "Solo puedes consultar tu propio historial de entrenamiento");
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }

        return trainingSessionRepository.findByPlayerIdOrderByCreatedAtDesc(playerId, pageable)
                .map(session -> modelMapper.map(session, TrainingSessionResponseDTO.class));
    }

    @Override
    public TrainingSessionResponseDTO getBestSessionByPlayer(Long playerId) {
        playerAccessGuard.requireSelfOrAdmin(playerId, "Solo puedes consultar el récord de tu propio entrenamiento");
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId);
        }

        TrainingSession bestSession = trainingSessionRepository.findTopByPlayerIdOrderByPostureScoreDesc(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontraron sesiones de entrenamiento para el jugador con ID: " + playerId));

        TrainingSessionResponseDTO response = modelMapper.map(bestSession, TrainingSessionResponseDTO.class);
        response.setNewPersonalBest(true);
        return response;
    }
}

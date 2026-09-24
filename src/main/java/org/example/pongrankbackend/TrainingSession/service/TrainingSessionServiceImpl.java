package org.example.pongrankbackend.TrainingSession.service;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.TrainingSession.TrainingSession;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionCreateDTO;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionResponseDTO;
import org.example.pongrankbackend.TrainingSession.repository.TrainingSessionRepository;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TrainingSessionServiceImpl implements TrainingSessionService {

    private final TrainingSessionRepository trainingSessionRepository;
    private final PlayerRepository playerRepository;
    private final ModelMapper modelMapper;

    public TrainingSessionServiceImpl(TrainingSessionRepository trainingSessionRepository,
                                       PlayerRepository playerRepository,
                                       ModelMapper modelMapper) {
        this.trainingSessionRepository = trainingSessionRepository;
        this.playerRepository = playerRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    @Transactional
    public TrainingSessionResponseDTO registerSession(Long playerId, TrainingSessionCreateDTO dto) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId));

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
}

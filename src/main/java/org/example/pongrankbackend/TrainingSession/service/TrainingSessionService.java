package org.example.pongrankbackend.TrainingSession.service;

import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionCreateDTO;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TrainingSessionService {

    TrainingSessionResponseDTO registerSession(Long playerId, TrainingSessionCreateDTO dto);

    List<TrainingSessionResponseDTO> getHistoryByPlayer(Long playerId);

    Page<TrainingSessionResponseDTO> getHistoryByPlayer(Long playerId, Pageable pageable);

    TrainingSessionResponseDTO getBestSessionByPlayer(Long playerId);
}

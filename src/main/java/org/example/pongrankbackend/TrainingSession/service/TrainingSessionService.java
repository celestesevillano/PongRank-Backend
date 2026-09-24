package org.example.pongrankbackend.TrainingSession.service;

import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionCreateDTO;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionResponseDTO;

import java.util.List;

public interface TrainingSessionService {

    TrainingSessionResponseDTO registerSession(Long playerId, TrainingSessionCreateDTO dto);

    List<TrainingSessionResponseDTO> getHistoryByPlayer(Long playerId);
}

package org.example.pongrankbackend.Match.service;

import org.example.pongrankbackend.Match.dto.MatchJoinRequestDTO;
import org.example.pongrankbackend.Match.dto.MatchResponseDTO;

import java.math.BigDecimal;
import java.util.List;

public interface OpenMatchService {

    // Partidos LOCATION abiertos (sin oponente) cercanos y de nivel similar al del jugador que busca
    List<MatchResponseDTO> getOpenLocationMatchesNearby(Long requesterId, BigDecimal latitude, BigDecimal longitude);

    MatchResponseDTO joinOpenMatch(Long joinerId, Long matchId, MatchJoinRequestDTO dto);
}

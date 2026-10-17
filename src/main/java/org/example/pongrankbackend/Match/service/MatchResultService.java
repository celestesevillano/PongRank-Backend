package org.example.pongrankbackend.Match.service;

import org.example.pongrankbackend.Match.dto.MatchDetailResponseDTO;
import org.example.pongrankbackend.Match.dto.MatchDisputeRequestDTO;
import org.example.pongrankbackend.Match.dto.MatchDisputeResolutionDTO;
import org.example.pongrankbackend.Match.dto.MatchResponseDTO;
import org.example.pongrankbackend.Match.dto.MatchScoreSubmitDTO;

public interface MatchResultService {

    MatchDetailResponseDTO submitScore(Long matchId, Long submittingPlayerId, MatchScoreSubmitDTO dto);

    MatchDetailResponseDTO confirmMatch(Long matchId, Long actingPlayerId);

    MatchDetailResponseDTO disputeMatch(Long matchId, Long actingPlayerId, MatchDisputeRequestDTO dto);

    // Solo SYSTEM_ADMIN: resuelve un partido DISPUTED declarando ganador (confirma y recalcula rating) o anulándolo
    MatchDetailResponseDTO resolveDispute(Long matchId, MatchDisputeResolutionDTO dto);

    MatchResponseDTO closeMatchAsWalkover(Long matchId, Long winnerId);
}

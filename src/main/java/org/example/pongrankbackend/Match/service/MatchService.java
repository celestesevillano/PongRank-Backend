package org.example.pongrankbackend.Match.service;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.dto.*;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Tournament.Tournament;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MatchService {

    MatchResponseDTO createMatch(Long creatorPlayerId, MatchCreateRequestDTO dto);

    Match createTournamentMatch(Player player1, Player player2, Tournament tournament, MatchFormat format);

    Match createTournamentMatch(Player player1, Player player2, MatchFormat format);

    MatchDetailResponseDTO getMatchById(Long matchId);

    List<MatchResponseDTO> getMatchesByPlayer(Long playerId);

    List<MatchResponseDTO> getMatchesByPlayerAndStatus(Long playerId, MatchStatus status);

    Page<MatchResponseDTO> getMatchesByPlayer(Long playerId, Pageable pageable);

    Page<MatchResponseDTO> getMatchesByPlayerAndStatus(Long playerId, MatchStatus status, Pageable pageable);

    Page<MatchResponseDTO> getAllMatches(Pageable pageable);

    Page<MatchResponseDTO> getMatchesByTournament(Long tournamentId, Pageable pageable);

    MatchDetailResponseDTO submitScore(Long matchId, Long submittingPlayerId, MatchScoreSubmitDTO dto);

    MatchDetailResponseDTO confirmMatch(Long matchId, Long actingPlayerId);

    MatchDetailResponseDTO disputeMatch(Long matchId, Long actingPlayerId, MatchDisputeRequestDTO dto);

    MatchResponseDTO cancelMatch(Long matchId, Long actingPlayerId);

    MatchResponseDTO closeMatchAsWalkover(Long matchId, Long winnerId);
}

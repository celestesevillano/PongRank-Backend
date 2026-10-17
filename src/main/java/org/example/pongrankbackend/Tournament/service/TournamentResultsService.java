package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Tournament.dto.TournamentMatchResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentWalkoverRequestDTO;

public interface TournamentResultsService {

    TournamentMatchResponseDTO declareWalkover(Long tournamentId, Long tournamentMatchId,
                                               TournamentWalkoverRequestDTO dto);

    TournamentResponseDTO syncMatchResults(Long tournamentId);

    void applyConfirmedMatch(Long matchId);
}

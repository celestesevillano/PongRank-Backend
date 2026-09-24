package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Tournament.dto.GroupStandingsResponseDTO;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentCreateRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentMatchResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentOrderedPlayersRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentWalkoverRequestDTO;

import java.util.List;

public interface TournamentService {

    TournamentResponseDTO createTournament(Long actingPlayerId, TournamentCreateRequestDTO dto);

    TournamentResponseDTO getTournamentById(Long tournamentId);

    PageResponseDTO<TournamentResponseDTO> getClubTournaments(Long clubId, int page, int size);

    List<TournamentParticipantResponseDTO> getParticipants(Long tournamentId);

    TournamentParticipantResponseDTO addParticipant(Long tournamentId, Long actingPlayerId, TournamentParticipantRequestDTO dto);

    void removeParticipant(Long tournamentId, Long playerId, Long actingPlayerId);

    List<TournamentParticipantResponseDTO> updateSeeding(Long tournamentId, Long actingPlayerId, TournamentOrderedPlayersRequestDTO dto);

    TournamentResponseDTO startTournament(Long tournamentId, Long actingPlayerId);

    List<TournamentMatchResponseDTO> getMatches(Long tournamentId);

    List<GroupStandingsResponseDTO> getGroupStandings(Long tournamentId);

    List<GroupStandingsResponseDTO> resolveGroupTie(Long tournamentId, Integer groupNumber, Long actingPlayerId,
                                                    TournamentOrderedPlayersRequestDTO dto);

    TournamentResponseDTO generateKnockout(Long tournamentId, Long actingPlayerId, boolean allowSameGroupMatches);

    TournamentMatchResponseDTO declareWalkover(Long tournamentId, Long tournamentMatchId, Long actingPlayerId,
                                               TournamentWalkoverRequestDTO dto);

    TournamentResponseDTO syncMatchResults(Long tournamentId, Long actingPlayerId);

    // Integration hook for the Match module: call it when a Match becomes CONFIRMED (not exposed through HTTP)
    void applyConfirmedMatch(Long matchId);
}

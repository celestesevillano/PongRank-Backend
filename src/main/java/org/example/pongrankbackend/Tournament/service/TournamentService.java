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

// El jugador que actúa se lee del SecurityContext (SecurityUtils), no se recibe como parámetro.
public interface TournamentService {

    TournamentResponseDTO createTournament(TournamentCreateRequestDTO dto);

    TournamentResponseDTO getTournamentById(Long tournamentId);

    PageResponseDTO<TournamentResponseDTO> getClubTournaments(Long clubId, int page, int size);

    List<TournamentParticipantResponseDTO> getParticipants(Long tournamentId);

    TournamentParticipantResponseDTO addParticipant(Long tournamentId, TournamentParticipantRequestDTO dto);

    void removeParticipant(Long tournamentId, Long playerId);

    List<TournamentParticipantResponseDTO> updateSeeding(Long tournamentId, TournamentOrderedPlayersRequestDTO dto);

    TournamentResponseDTO startTournament(Long tournamentId);

    List<TournamentMatchResponseDTO> getMatches(Long tournamentId);

    List<GroupStandingsResponseDTO> getGroupStandings(Long tournamentId);

    List<GroupStandingsResponseDTO> resolveGroupTie(Long tournamentId, Integer groupNumber,
                                                    TournamentOrderedPlayersRequestDTO dto);

    TournamentResponseDTO generateKnockout(Long tournamentId, boolean allowSameGroupMatches);

    TournamentMatchResponseDTO declareWalkover(Long tournamentId, Long tournamentMatchId,
                                               TournamentWalkoverRequestDTO dto);

    TournamentResponseDTO syncMatchResults(Long tournamentId);

    // Called by TournamentMatchEventListener when the Match module publishes MatchConfirmedEvent (not exposed through HTTP)
    void applyConfirmedMatch(Long matchId);
}

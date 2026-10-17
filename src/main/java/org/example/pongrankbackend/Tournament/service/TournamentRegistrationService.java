package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Tournament.dto.TournamentCreateRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentOrderedPlayersRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;

import java.util.*;
import java.util.List;

public interface TournamentRegistrationService {

    TournamentResponseDTO createTournament(TournamentCreateRequestDTO dto);

    TournamentParticipantResponseDTO addParticipant(Long tournamentId, TournamentParticipantRequestDTO dto);

    void removeParticipant(Long tournamentId, Long playerId);

    List<TournamentParticipantResponseDTO> updateSeeding(Long tournamentId, TournamentOrderedPlayersRequestDTO dto);
}

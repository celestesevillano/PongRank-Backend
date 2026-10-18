package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Tournament.dto.GroupStandingsResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentOrderedPlayersRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;

import java.util.*;
import java.util.List;

public interface TournamentBracketService {

    TournamentResponseDTO startTournament(Long tournamentId);

    List<GroupStandingsResponseDTO> resolveGroupTie(Long tournamentId, Integer groupNumber,
                                                    TournamentOrderedPlayersRequestDTO dto);

    TournamentResponseDTO generateKnockout(Long tournamentId, boolean allowSameGroupMatches);
}

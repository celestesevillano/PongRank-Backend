package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.Tournament.dto.GroupStandingsResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentCreateRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentMatchResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentOrderedPlayersRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentWalkoverRequestDTO;
import org.example.pongrankbackend.Tournament.repository.TournamentMatchRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentParticipantRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentRepository;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class TournamentServiceImpl implements TournamentService {

    private final ClubRepository clubRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final TournamentAccessPolicy accessPolicy;
    private final TournamentMapper tournamentMapper;
    private final TournamentGroupStandings groupStandings;
    private final TournamentRegistrationService registrationService;
    private final TournamentBracketService bracketService;
    private final TournamentResultsService resultsService;

    public TournamentServiceImpl(ClubRepository clubRepository,
                                 TournamentRepository tournamentRepository,
                                 TournamentParticipantRepository participantRepository,
                                 TournamentMatchRepository tournamentMatchRepository,
                                 TournamentAccessPolicy accessPolicy,
                                 TournamentMapper tournamentMapper,
                                 TournamentGroupStandings groupStandings,
                                 TournamentRegistrationService registrationService,
                                 TournamentBracketService bracketService,
                                 TournamentResultsService resultsService) {
        this.clubRepository = clubRepository;
        this.tournamentRepository = tournamentRepository;
        this.participantRepository = participantRepository;
        this.tournamentMatchRepository = tournamentMatchRepository;
        this.accessPolicy = accessPolicy;
        this.tournamentMapper = tournamentMapper;
        this.groupStandings = groupStandings;
        this.registrationService = registrationService;
        this.bracketService = bracketService;
        this.resultsService = resultsService;
    }

    @Override
    public TournamentResponseDTO getTournamentById(Long tournamentId) {
        return tournamentMapper.toResponse(accessPolicy.findTournament(tournamentId));
    }

    @Override
    public PageResponseDTO<TournamentResponseDTO> getClubTournaments(Long clubId, int page, int size) {
        if (!clubRepository.existsById(clubId)) {
            throw new ResourceNotFoundException("Club no encontrado con ID: " + clubId);
        }
        return PageResponseDTO.from(
                tournamentRepository.findByClubId(clubId, PageRequestFactory.of(page, size, Sort.by("createdAt").descending())),
                tournamentMapper::toResponse);
    }

    @Override
    public List<TournamentParticipantResponseDTO> getParticipants(Long tournamentId) {
        accessPolicy.findTournament(tournamentId);
        return participantRepository.findByTournamentIdOrderBySeedAsc(tournamentId).stream()
                .map(tournamentMapper::toParticipantResponse)
                .toList();
    }

    @Override
    public List<TournamentMatchResponseDTO> getMatches(Long tournamentId) {
        accessPolicy.findTournament(tournamentId);
        return tournamentMatchRepository.findByTournamentIdOrderByIdAsc(tournamentId).stream()
                .map(tournamentMapper::toMatchResponse)
                .toList();
    }

    @Override
    public List<GroupStandingsResponseDTO> getGroupStandings(Long tournamentId) {
        return groupStandings.toStandingsResponse(groupStandings.calculateGroups(accessPolicy.findTournament(tournamentId)));
    }

    @Override
    @Transactional
    public TournamentResponseDTO createTournament(TournamentCreateRequestDTO dto) {
        return registrationService.createTournament(dto);
    }

    @Override
    @Transactional
    public TournamentParticipantResponseDTO addParticipant(Long tournamentId, TournamentParticipantRequestDTO dto) {
        return registrationService.addParticipant(tournamentId, dto);
    }

    @Override
    @Transactional
    public void removeParticipant(Long tournamentId, Long playerId) {
        registrationService.removeParticipant(tournamentId, playerId);
    }

    @Override
    @Transactional
    public List<TournamentParticipantResponseDTO> updateSeeding(Long tournamentId, TournamentOrderedPlayersRequestDTO dto) {
        return registrationService.updateSeeding(tournamentId, dto);
    }

    @Override
    @Transactional
    public TournamentResponseDTO startTournament(Long tournamentId) {
        return bracketService.startTournament(tournamentId);
    }

    @Override
    @Transactional
    public List<GroupStandingsResponseDTO> resolveGroupTie(Long tournamentId, Integer groupNumber,
                                                           TournamentOrderedPlayersRequestDTO dto) {
        return bracketService.resolveGroupTie(tournamentId, groupNumber, dto);
    }

    @Override
    @Transactional
    public TournamentResponseDTO generateKnockout(Long tournamentId, boolean allowSameGroupMatches) {
        return bracketService.generateKnockout(tournamentId, allowSameGroupMatches);
    }

    @Override
    @Transactional
    public TournamentMatchResponseDTO declareWalkover(Long tournamentId, Long tournamentMatchId,
                                                      TournamentWalkoverRequestDTO dto) {
        return resultsService.declareWalkover(tournamentId, tournamentMatchId, dto);
    }

    @Override
    @Transactional
    public TournamentResponseDTO syncMatchResults(Long tournamentId) {
        return resultsService.syncMatchResults(tournamentId);
    }

    // Runs in its own transaction because it is called after the Match transaction has committed
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyConfirmedMatch(Long matchId) {
        resultsService.applyConfirmedMatch(matchId);
    }
}

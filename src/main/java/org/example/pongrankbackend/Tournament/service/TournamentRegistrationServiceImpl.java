package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.Tournament.TournamentParticipant;
import org.example.pongrankbackend.Tournament.TournamentStatus;
import org.example.pongrankbackend.Tournament.dto.TournamentCreateRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentOrderedPlayersRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.example.pongrankbackend.Tournament.repository.TournamentParticipantRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentRepository;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TournamentRegistrationServiceImpl implements TournamentRegistrationService {

    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final TournamentAccessPolicy accessPolicy;
    private final TournamentMapper tournamentMapper;

    public TournamentRegistrationServiceImpl(TournamentRepository tournamentRepository,
                                             TournamentParticipantRepository participantRepository,
                                             ClubRepository clubRepository,
                                             PlayerRepository playerRepository,
                                             TournamentAccessPolicy accessPolicy,
                                             TournamentMapper tournamentMapper) {
        this.tournamentRepository = tournamentRepository;
        this.participantRepository = participantRepository;
        this.clubRepository = clubRepository;
        this.playerRepository = playerRepository;
        this.accessPolicy = accessPolicy;
        this.tournamentMapper = tournamentMapper;
    }

    @Override
    @Transactional
    public TournamentResponseDTO createTournament(TournamentCreateRequestDTO dto) {
        Club club = clubRepository.findById(dto.getClubId())
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado con ID: " + dto.getClubId()));
        accessPolicy.validateClubAdmin(club, SecurityUtils.getRequiredCurrentUserId());

        if (!club.canOrganizeTournaments()) {
            throw new ConflictException("Solo un club APPROVED puede organizar torneos");
        }

        Tournament tournament = Tournament.builder()
                .name(dto.getName().trim())
                .club(club)
                .type(dto.getType())
                .matchFormat(dto.getMatchFormat())
                .status(TournamentStatus.OPEN)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .build();

        return tournamentMapper.toResponse(tournamentRepository.save(tournament));
    }

    @Override
    @Transactional
    public TournamentParticipantResponseDTO addParticipant(Long tournamentId, TournamentParticipantRequestDTO dto) {
        Tournament tournament = accessPolicy.findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        accessPolicy.validateStatus(tournament, TournamentStatus.OPEN, "Las inscripciones del torneo ya están cerradas");

        Player player = playerRepository.findById(dto.getPlayerId())
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + dto.getPlayerId()));
        String eligibilityError = accessPolicy.eligibilityError(tournament, player);
        if (eligibilityError != null) {
            throw new ConflictException(eligibilityError);
        }

        if (participantRepository.existsByTournamentIdAndPlayerId(tournamentId, player.getId())) {
            throw new ConflictException("El jugador ya está inscrito en el torneo");
        }

        List<TournamentParticipant> participants = new ArrayList<>(participantRepository.findByTournamentIdOrderBySeedAsc(tournamentId));
        TournamentParticipant participant = TournamentParticipant.builder()
                .tournament(tournament)
                .player(player)
                .seed(participants.size() + 1)
                .build();
        participants.add(participant);

        if (!tournament.getManualSeeding()) {
            reseedByRating(participants);
        }
        participantRepository.saveAll(participants);

        return tournamentMapper.toParticipantResponse(participant);
    }

    @Override
    @Transactional
    public void removeParticipant(Long tournamentId, Long playerId) {
        Tournament tournament = accessPolicy.findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        accessPolicy.validateStatus(tournament, TournamentStatus.OPEN, "La lista de participantes queda cerrada una vez iniciado el torneo");

        TournamentParticipant participant = participantRepository.findByTournamentIdAndPlayerId(tournamentId, playerId)
                .orElseThrow(() -> new ResourceNotFoundException("El jugador no está inscrito en el torneo"));
        participantRepository.delete(participant);

        List<TournamentParticipant> remaining = participantRepository.findByTournamentIdOrderBySeedAsc(tournamentId).stream()
                .filter(p -> !p.getId().equals(participant.getId()))
                .collect(Collectors.toCollection(ArrayList::new));

        if (tournament.getManualSeeding()) {
            renumberSeeds(remaining);
        } else {
            reseedByRating(remaining);
        }
        participantRepository.saveAll(remaining);
    }

    @Override
    @Transactional
    public List<TournamentParticipantResponseDTO> updateSeeding(Long tournamentId, TournamentOrderedPlayersRequestDTO dto) {
        Tournament tournament = accessPolicy.findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        accessPolicy.validateStatus(tournament, TournamentStatus.OPEN, "La siembra queda bloqueada una vez iniciado el torneo");

        List<TournamentParticipant> participants = participantRepository.findByTournamentIdOrderBySeedAsc(tournamentId);
        Map<Long, TournamentParticipant> byPlayer = TournamentParticipants.indexByPlayer(participants);
        List<Long> ordered = dto.getOrderedPlayerIds();

        if (ordered.size() != participants.size() || !new HashSet<>(ordered).equals(byPlayer.keySet())) {
            throw new ConflictException("La siembra debe incluir a todos los participantes exactamente una vez");
        }

        for (int i = 0; i < ordered.size(); i++) {
            byPlayer.get(ordered.get(i)).setSeed(i + 1);
        }
        tournament.setManualSeeding(true);
        tournamentRepository.save(tournament);
        participantRepository.saveAll(participants);

        return participants.stream()
                .sorted(Comparator.comparing(TournamentParticipant::getSeed))
                .map(tournamentMapper::toParticipantResponse)
                .toList();
    }

    // Default seeding: Glicko rating (highest first); equal ratings keep registration order
    private void reseedByRating(List<TournamentParticipant> participants) {
        participants.sort(Comparator
                .comparing((TournamentParticipant p) -> p.getPlayer().getRatingGlicko(), Comparator.reverseOrder())
                .thenComparing(p -> p.getId() == null ? Long.MAX_VALUE : p.getId()));
        renumberSeeds(participants);
    }

    private void renumberSeeds(List<TournamentParticipant> participants) {
        for (int i = 0; i < participants.size(); i++) {
            participants.get(i).setSeed(i + 1);
        }
    }
}

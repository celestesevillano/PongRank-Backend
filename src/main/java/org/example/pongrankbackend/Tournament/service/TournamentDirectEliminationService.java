package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.Tournament.TournamentMatch;
import org.example.pongrankbackend.Tournament.TournamentMatchStatus;
import org.example.pongrankbackend.Tournament.TournamentParticipant;
import org.example.pongrankbackend.Tournament.TournamentStage;
import org.example.pongrankbackend.Tournament.TournamentStatus;
import org.example.pongrankbackend.Tournament.TournamentType;
import org.example.pongrankbackend.Tournament.dto.DirectEliminationCreateRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentMatchResponseDTO;
import org.example.pongrankbackend.Tournament.integration.MatchIntegrationPort;
import org.example.pongrankbackend.common.exception.InvalidMatchStateException;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.example.pongrankbackend.Tournament.engine.DirectEliminationBracketBuilder;
import org.example.pongrankbackend.Tournament.engine.DirectEliminationBracketBuilder.Pairing;
import org.example.pongrankbackend.Tournament.engine.DirectEliminationBracketBuilder.Plan;
import org.example.pongrankbackend.Tournament.repository.TournamentMatchRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentParticipantRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentRepository;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Publishes a single-elimination tournament in one step: tournament, participants and the full bracket
@Service
@Transactional(readOnly = true)
public class TournamentDirectEliminationService {

    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final TournamentAccessPolicy accessPolicy;
    private final TournamentMapper tournamentMapper;
    private final TournamentMatchScheduler matchScheduler;
    private final DirectEliminationBracketBuilder bracketBuilder;
    private final MatchIntegrationPort matchIntegrationPort;

    public TournamentDirectEliminationService(ClubRepository clubRepository,
                                              PlayerRepository playerRepository,
                                              TournamentRepository tournamentRepository,
                                              TournamentParticipantRepository participantRepository,
                                              TournamentMatchRepository tournamentMatchRepository,
                                              TournamentAccessPolicy accessPolicy,
                                              TournamentMapper tournamentMapper,
                                              TournamentMatchScheduler matchScheduler,
                                              DirectEliminationBracketBuilder bracketBuilder,
                                              MatchIntegrationPort matchIntegrationPort) {
        this.clubRepository = clubRepository;
        this.playerRepository = playerRepository;
        this.tournamentRepository = tournamentRepository;
        this.participantRepository = participantRepository;
        this.tournamentMatchRepository = tournamentMatchRepository;
        this.accessPolicy = accessPolicy;
        this.tournamentMapper = tournamentMapper;
        this.matchScheduler = matchScheduler;
        this.bracketBuilder = bracketBuilder;
        this.matchIntegrationPort = matchIntegrationPort;
    }

    @Transactional
    public TournamentResponseDTO publish(DirectEliminationCreateRequestDTO dto) {
        Club club = clubRepository.findById(dto.getClubId())
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado con ID: " + dto.getClubId()));
        accessPolicy.validateClubAdmin(club, SecurityUtils.getRequiredCurrentUserId());
        if (!club.canOrganizeTournaments()) {
            throw new ConflictException("Solo un club APPROVED puede organizar torneos");
        }

        String key = dto.getPublicationKey() == null || dto.getPublicationKey().isBlank() ? null : dto.getPublicationKey().trim();
        if (key != null) {
            Optional<Tournament> existing = tournamentRepository.findFirstByClubIdAndPublicationKey(club.getId(), key);
            if (existing.isPresent()) {
                return tournamentMapper.toResponse(existing.get());
            }
        }

        List<Long> playerIds = dto.getPlayerIds();
        if (new HashSet<>(playerIds).size() != playerIds.size()) {
            throw new ConflictException("Hay participantes repetidos en la lista");
        }
        String countError = bracketBuilder.validate(playerIds.size());
        if (countError != null) {
            throw new ConflictException(countError);
        }

        List<Player> players = playerRepository.findAllById(playerIds);
        if (players.size() != playerIds.size()) {
            throw new ResourceNotFoundException("Alguno de los participantes no existe");
        }

        Tournament tournament = Tournament.builder()
                .name(dto.getName().trim())
                .club(club)
                .type(TournamentType.INTERNAL)
                .matchFormat(dto.getMatchFormat())
                .status(TournamentStatus.KNOCKOUT_STAGE)
                .directElimination(true)
                .publicationKey(key)
                .startDate(LocalDateTime.ofInstant(dto.getScheduledAt(), ZoneOffset.UTC))
                .build();

        List<String> ineligible = players.stream()
                .map(p -> accessPolicy.eligibilityError(tournament, p))
                .filter(error -> error != null)
                .toList();
        if (!ineligible.isEmpty()) {
            throw new ConflictException(ineligible.get(0));
        }
        tournamentRepository.save(tournament);

        // Seed 1 is the highest rating; equal ratings keep a stable order by id
        List<Player> ranked = new ArrayList<>(players);
        ranked.sort(Comparator.comparing(Player::getRatingGlicko, Comparator.reverseOrder()).thenComparing(Player::getId));
        List<TournamentParticipant> participants = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            participants.add(TournamentParticipant.builder().tournament(tournament).player(ranked.get(i)).seed(i + 1).build());
        }
        participantRepository.saveAll(participants);

        buildBracket(tournament, ranked, dto);
        return tournamentMapper.toResponse(tournament);
    }

    private void buildBracket(Tournament tournament, List<Player> ranked, DirectEliminationCreateRequestDTO dto) {
        Plan plan = bracketBuilder.build(ranked.size());
        Map<String, TournamentMatch> bracket = new HashMap<>();
        List<TournamentMatch> all = new ArrayList<>();
        for (Pairing pairing : plan.pairings()) {
            TournamentMatch match = TournamentMatch.builder()
                    .tournament(tournament)
                    .stage(TournamentStage.KNOCKOUT)
                    .round(pairing.round())
                    .bracketPosition(pairing.position())
                    .player1(pairing.seed1() == null ? null : ranked.get(pairing.seed1() - 1))
                    .player2(pairing.seed2() == null ? null : ranked.get(pairing.seed2() - 1))
                    .build();
            bracket.put(pairing.round() + "-" + pairing.position(), match);
            all.add(match);
        }
        all.stream()
                .filter(m -> m.getPlayer1() != null && m.getPlayer2() != null && m.getStatus() == TournamentMatchStatus.PENDING_PLAYERS)
                .forEach(m -> {
                    matchScheduler.schedule(tournament, m);
                    m.getMatch().setScheduledAt(dto.getScheduledAt());
                });
        tournamentMatchRepository.saveAll(all);
    }

    @Transactional
    public TournamentMatchResponseDTO withdraw(Long matchId) {
        Long playerId = SecurityUtils.getRequiredCurrentUserId();
        TournamentMatch match = tournamentMatchRepository.findByMatchId(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido del torneo no encontrado para el partido: " + matchId));
        Tournament tournament = match.getTournament();
        accessPolicy.validateInProgress(tournament);
        if (match.getStatus() != TournamentMatchStatus.SCHEDULED) {
            throw new ConflictException("Solo puedes retirarte de un partido programado y sin resultado");
        }
        if (!match.hasPlayer(playerId)) {
            throw new InvalidMatchStateException("No participas en este partido");
        }
        if (matchIntegrationPort.hasReportedScore(match.getMatch())) {
            throw new ConflictException("El partido ya tiene un marcador reportado; no puedes retirarte");
        }

        boolean player1Withdraws = match.getPlayer1().getId().equals(playerId);
        match.setWinner(player1Withdraws ? match.getPlayer2() : match.getPlayer1());
        match.setStatus(TournamentMatchStatus.WALKOVER);
        if (match.getMatch() != null) {
            matchIntegrationPort.closeAsWalkover(match.getMatch(), match.getWinner());
        }

        advanceWinner(tournament, match);
        return tournamentMapper.toMatchResponse(tournamentMatchRepository.save(match));
    }

    // The winner moves to the next round (a preliminary match feeds the main-round slot stored in bracketPosition)
    private void advanceWinner(Tournament tournament, TournamentMatch match) {
        Optional<TournamentMatch> next = tournamentMatchRepository.findByTournamentIdAndStageAndRoundAndBracketPosition(
                tournament.getId(), TournamentStage.KNOCKOUT, match.getRound() + 1, match.getBracketPosition() / 2);
        if (next.isEmpty()) {
            tournament.setWinner(match.getWinner());
            tournament.setStatus(TournamentStatus.FINISHED);
            tournamentRepository.save(tournament);
            return;
        }
        TournamentMatch nextMatch = next.get();
        matchScheduler.placeInNextMatch(match, nextMatch);
        if (nextMatch.getPlayer1() != null && nextMatch.getPlayer2() != null
                && nextMatch.getStatus() == TournamentMatchStatus.PENDING_PLAYERS) {
            matchScheduler.schedule(tournament, nextMatch);
        }
        tournamentMatchRepository.save(nextMatch);
    }
}

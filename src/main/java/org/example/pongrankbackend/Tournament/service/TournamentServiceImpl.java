package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.ClubMembership.service.ClubMembershipService;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.Tournament.TournamentMatch;
import org.example.pongrankbackend.Tournament.TournamentMatchStatus;
import org.example.pongrankbackend.Tournament.TournamentParticipant;
import org.example.pongrankbackend.Tournament.TournamentStage;
import org.example.pongrankbackend.Tournament.TournamentStatus;
import org.example.pongrankbackend.Tournament.TournamentType;
import org.example.pongrankbackend.Tournament.dto.GroupStandingRowDTO;
import org.example.pongrankbackend.Tournament.dto.GroupStandingsResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentCreateRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentMatchResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentOrderedPlayersRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentParticipantResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentWalkoverRequestDTO;
import org.example.pongrankbackend.Tournament.engine.GroupDistributor;
import org.example.pongrankbackend.Tournament.engine.GroupStandingsCalculator;
import org.example.pongrankbackend.Tournament.engine.GroupStandingsCalculator.GroupMatchResult;
import org.example.pongrankbackend.Tournament.engine.GroupStandingsCalculator.GroupStandings;
import org.example.pongrankbackend.Tournament.engine.GroupStandingsCalculator.StandingRow;
import org.example.pongrankbackend.Tournament.engine.KnockoutBracketBuilder;
import org.example.pongrankbackend.Tournament.engine.KnockoutBracketBuilder.BracketPlan;
import org.example.pongrankbackend.Tournament.engine.KnockoutBracketBuilder.FirstRoundPairing;
import org.example.pongrankbackend.Tournament.engine.KnockoutBracketBuilder.Qualifier;
import org.example.pongrankbackend.Tournament.engine.RoundRobinScheduler;
import org.example.pongrankbackend.Tournament.integration.MatchIntegrationPort;
import org.example.pongrankbackend.Tournament.integration.MatchOutcome;
import org.example.pongrankbackend.Tournament.repository.TournamentMatchRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentParticipantRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentRepository;
import org.example.pongrankbackend.security.SecurityUtils;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.InvalidMatchStateException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TournamentServiceImpl implements TournamentService {

    private static final int QUALIFIERS_PER_GROUP = 2;

    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final ClubRepository clubRepository;
    private final PlayerRepository playerRepository;
    private final ClubMembershipService clubMembershipService;
    private final MatchIntegrationPort matchIntegrationPort;
    private final GroupDistributor groupDistributor;
    private final RoundRobinScheduler roundRobinScheduler;
    private final GroupStandingsCalculator standingsCalculator;
    private final KnockoutBracketBuilder bracketBuilder;
    private final ModelMapper modelMapper;

    public TournamentServiceImpl(TournamentRepository tournamentRepository,
                                 TournamentParticipantRepository participantRepository,
                                 TournamentMatchRepository tournamentMatchRepository,
                                 ClubRepository clubRepository,
                                 PlayerRepository playerRepository,
                                 ClubMembershipService clubMembershipService,
                                 MatchIntegrationPort matchIntegrationPort,
                                 GroupDistributor groupDistributor,
                                 RoundRobinScheduler roundRobinScheduler,
                                 GroupStandingsCalculator standingsCalculator,
                                 KnockoutBracketBuilder bracketBuilder,
                                 ModelMapper modelMapper) {
        this.tournamentRepository = tournamentRepository;
        this.participantRepository = participantRepository;
        this.tournamentMatchRepository = tournamentMatchRepository;
        this.clubRepository = clubRepository;
        this.playerRepository = playerRepository;
        this.clubMembershipService = clubMembershipService;
        this.matchIntegrationPort = matchIntegrationPort;
        this.groupDistributor = groupDistributor;
        this.roundRobinScheduler = roundRobinScheduler;
        this.standingsCalculator = standingsCalculator;
        this.bracketBuilder = bracketBuilder;
        this.modelMapper = modelMapper;
    }

    // ------------------------------------------------------------------ organization

    @Override
    @Transactional
    public TournamentResponseDTO createTournament(TournamentCreateRequestDTO dto) {
        Club club = clubRepository.findById(dto.getClubId())
                .orElseThrow(() -> new ResourceNotFoundException("Club no encontrado con ID: " + dto.getClubId()));
        validateClubAdmin(club, SecurityUtils.getRequiredCurrentUserId());

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

        return toResponse(tournamentRepository.save(tournament));
    }

    @Override
    public TournamentResponseDTO getTournamentById(Long tournamentId) {
        return toResponse(findTournament(tournamentId));
    }

    @Override
    public PageResponseDTO<TournamentResponseDTO> getClubTournaments(Long clubId, int page, int size) {
        if (!clubRepository.existsById(clubId)) {
            throw new ResourceNotFoundException("Club no encontrado con ID: " + clubId);
        }
        return PageResponseDTO.from(
                tournamentRepository.findByClubId(clubId, PageRequestFactory.of(page, size, Sort.by("createdAt").descending())),
                this::toResponse);
    }

    // ------------------------------------------------------------------ participants

    @Override
    public List<TournamentParticipantResponseDTO> getParticipants(Long tournamentId) {
        findTournament(tournamentId);
        return participantRepository.findByTournamentIdOrderBySeedAsc(tournamentId).stream()
                .map(this::toParticipantResponse)
                .toList();
    }

    @Override
    @Transactional
    public TournamentParticipantResponseDTO addParticipant(Long tournamentId, TournamentParticipantRequestDTO dto) {
        Tournament tournament = findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        validateStatus(tournament, TournamentStatus.OPEN, "Las inscripciones del torneo ya están cerradas");

        Player player = playerRepository.findById(dto.getPlayerId())
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + dto.getPlayerId()));
        String eligibilityError = eligibilityError(tournament, player);
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

        return toParticipantResponse(participant);
    }

    @Override
    @Transactional
    public void removeParticipant(Long tournamentId, Long playerId) {
        Tournament tournament = findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        validateStatus(tournament, TournamentStatus.OPEN, "La lista de participantes queda cerrada una vez iniciado el torneo");

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
        Tournament tournament = findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        validateStatus(tournament, TournamentStatus.OPEN, "La siembra queda bloqueada una vez iniciado el torneo");

        List<TournamentParticipant> participants = participantRepository.findByTournamentIdOrderBySeedAsc(tournamentId);
        Map<Long, TournamentParticipant> byPlayer = indexByPlayer(participants);
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
                .map(this::toParticipantResponse)
                .toList();
    }

    // ------------------------------------------------------------------ group stage

    @Override
    @Transactional
    public TournamentResponseDTO startTournament(Long tournamentId) {
        Tournament tournament = findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        validateStatus(tournament, TournamentStatus.OPEN, "El torneo ya fue iniciado");

        List<TournamentParticipant> participants = participantRepository.findByTournamentIdOrderBySeedAsc(tournamentId);
        validateDistributable(participants);
        validateAllEligible(tournament, participants);

        Map<Long, TournamentParticipant> byPlayer = indexByPlayer(participants);
        List<List<Long>> groups = groupDistributor.distribute(participants.stream().map(p -> p.getPlayer().getId()).toList());
        List<TournamentMatch> fixtures = buildGroupStageFixtures(tournament, groups, byPlayer);

        participantRepository.saveAll(participants);
        tournamentMatchRepository.saveAll(fixtures);
        tournament.setStatus(TournamentStatus.GROUP_STAGE);
        return toResponse(tournamentRepository.save(tournament));
    }

    private void validateDistributable(List<TournamentParticipant> participants) {
        if (!groupDistributor.canDistribute(participants.size())) {
            throw new ConflictException("Se necesitan al menos 3 participantes y una cantidad que permita formar grupos de 3 o 4 jugadores (con "
                    + participants.size() + " no es posible)");
        }
    }

    private void validateAllEligible(Tournament tournament, List<TournamentParticipant> participants) {
        List<String> ineligible = participants.stream()
                .map(p -> eligibilityError(tournament, p.getPlayer()) == null ? null : p.getPlayer().getName())
                .filter(Objects::nonNull)
                .toList();
        if (!ineligible.isEmpty()) {
            throw new ConflictException("Estos participantes ya no cumplen los requisitos y deben retirarse antes de iniciar: " + ineligible);
        }
    }

    // Asigna número de grupo a cada participante y arma los fixtures round-robin de cada grupo
    private List<TournamentMatch> buildGroupStageFixtures(Tournament tournament, List<List<Long>> groups,
                                                           Map<Long, TournamentParticipant> byPlayer) {
        List<TournamentMatch> fixtures = new ArrayList<>();
        for (int g = 0; g < groups.size(); g++) {
            int groupNumber = g + 1;
            List<Long> group = groups.get(g);
            group.forEach(playerId -> byPlayer.get(playerId).setGroupNumber(groupNumber));

            for (RoundRobinScheduler.Pairing pairing : roundRobinScheduler.schedule(group)) {
                TournamentMatch fixture = TournamentMatch.builder()
                        .tournament(tournament)
                        .stage(TournamentStage.GROUP)
                        .groupNumber(groupNumber)
                        .player1(byPlayer.get(pairing.player1Id()).getPlayer())
                        .player2(byPlayer.get(pairing.player2Id()).getPlayer())
                        .build();
                schedule(tournament, fixture);
                fixtures.add(fixture);
            }
        }
        return fixtures;
    }

    @Override
    public List<TournamentMatchResponseDTO> getMatches(Long tournamentId) {
        findTournament(tournamentId);
        return tournamentMatchRepository.findByTournamentIdOrderByIdAsc(tournamentId).stream()
                .map(this::toMatchResponse)
                .toList();
    }

    @Override
    public List<GroupStandingsResponseDTO> getGroupStandings(Long tournamentId) {
        return toStandingsResponse(calculateGroups(findTournament(tournamentId)));
    }

    @Override
    @Transactional
    public List<GroupStandingsResponseDTO> resolveGroupTie(Long tournamentId, Integer groupNumber,
                                                           TournamentOrderedPlayersRequestDTO dto) {
        Tournament tournament = findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        validateStatus(tournament, TournamentStatus.GROUP_STAGE, "Los empates solo se resuelven durante la fase de grupos");

        GroupData group = calculateGroups(tournament).stream()
                .filter(g -> g.groupNumber() == groupNumber)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado: " + groupNumber));
        if (!group.completed()) {
            throw new ConflictException("El grupo todavía tiene partidos pendientes");
        }

        List<Long> ordered = dto.getOrderedPlayerIds();
        Set<Long> requested = new HashSet<>(ordered);
        boolean isUnresolvedTie = requested.size() == ordered.size() && group.standings().unresolvedTies().stream()
                .anyMatch(tie -> new HashSet<>(tie).equals(requested));
        if (!isUnresolvedTie) {
            throw new ConflictException("Los jugadores indicados no forman un empate sin resolver en el grupo " + groupNumber);
        }

        Map<Long, TournamentParticipant> byPlayer = indexByPlayer(group.participants());
        for (int i = 0; i < ordered.size(); i++) {
            byPlayer.get(ordered.get(i)).setTiebreakOrder(i + 1);
        }
        participantRepository.saveAll(group.participants());

        return toStandingsResponse(calculateGroups(tournament));
    }

    // ------------------------------------------------------------------ knockout stage

    @Override
    @Transactional
    public TournamentResponseDTO generateKnockout(Long tournamentId, boolean allowSameGroupMatches) {
        Tournament tournament = findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        validateStatus(tournament, TournamentStatus.GROUP_STAGE, "La llave solo se genera al terminar la fase de grupos");

        List<GroupData> groups = calculateGroups(tournament);
        validateGroupsCompleted(groups);
        validateNoBlockingTies(groups);

        Map<Long, TournamentParticipant> byPlayer = new HashMap<>();
        List<Qualifier> winners = new ArrayList<>();
        List<Qualifier> runnersUp = new ArrayList<>();
        extractQualifiers(groups, byPlayer, winners, runnersUp);

        BracketPlan plan = bracketBuilder.build(winners, runnersUp);
        validateNoSameGroupConflicts(plan, byPlayer, allowSameGroupMatches);

        Map<String, TournamentMatch> bracket = buildEmptyBracket(tournament, plan);
        populateFirstRound(bracket, plan, byPlayer);

        bracket.values().stream()
                .filter(m -> m.getStatus() == TournamentMatchStatus.PENDING_PLAYERS && m.getPlayer1() != null && m.getPlayer2() != null)
                .forEach(m -> schedule(tournament, m));

        tournamentMatchRepository.saveAll(bracket.values());
        tournament.setStatus(TournamentStatus.KNOCKOUT_STAGE);
        return toResponse(tournamentRepository.save(tournament));
    }

    private void validateGroupsCompleted(List<GroupData> groups) {
        if (groups.stream().anyMatch(g -> !g.completed())) {
            throw new ConflictException("Todos los partidos de la fase de grupos deben estar finalizados");
        }
    }

    private void validateNoBlockingTies(List<GroupData> groups) {
        List<Integer> blockedGroups = groups.stream()
                .filter(g -> g.standings().hasBlockingTie(QUALIFIERS_PER_GROUP))
                .map(GroupData::groupNumber)
                .toList();
        if (!blockedGroups.isEmpty()) {
            throw new ConflictException("Hay empates sin resolver que afectan la clasificación en los grupos " + blockedGroups
                    + ". El administrador debe resolverlos antes de generar la llave");
        }
    }

    // Separa a los primeros y segundos de cada grupo, que son los clasificados a la llave
    private void extractQualifiers(List<GroupData> groups, Map<Long, TournamentParticipant> byPlayer,
                                   List<Qualifier> winners, List<Qualifier> runnersUp) {
        for (GroupData group : groups) {
            byPlayer.putAll(indexByPlayer(group.participants()));
            for (StandingRow row : group.standings().rows()) {
                if (row.position() > QUALIFIERS_PER_GROUP) {
                    continue;
                }
                Qualifier qualifier = new Qualifier(row.playerId(), group.groupNumber(), byPlayer.get(row.playerId()).getSeed());
                (row.position() == 1 ? winners : runnersUp).add(qualifier);
            }
        }
    }

    private void validateNoSameGroupConflicts(BracketPlan plan, Map<Long, TournamentParticipant> byPlayer, boolean allowSameGroupMatches) {
        if (!plan.sameGroupConflicts().isEmpty() && !allowSameGroupMatches) {
            String conflicts = plan.sameGroupConflicts().stream()
                    .map(c -> byPlayer.get(c.player1().playerId()).getPlayer().getName() + " vs "
                            + byPlayer.get(c.player2().playerId()).getPlayer().getName() + " (grupo " + c.player1().groupNumber() + ")")
                    .collect(Collectors.joining(", "));
            throw new ConflictException("No existe una llave que evite cruces del mismo grupo en la primera ronda: " + conflicts
                    + ". Para generarla igualmente, repite la petición con allowSameGroupMatches=true");
        }
    }

    private Map<String, TournamentMatch> buildEmptyBracket(Tournament tournament, BracketPlan plan) {
        Map<String, TournamentMatch> bracket = new LinkedHashMap<>();
        for (int round = 1; round <= plan.rounds(); round++) {
            int matchesInRound = plan.bracketSize() >> round;
            for (int position = 0; position < matchesInRound; position++) {
                bracket.put(bracketKey(round, position), TournamentMatch.builder()
                        .tournament(tournament)
                        .stage(TournamentStage.KNOCKOUT)
                        .round(round)
                        .bracketPosition(position)
                        .build());
            }
        }
        return bracket;
    }

    private void populateFirstRound(Map<String, TournamentMatch> bracket, BracketPlan plan, Map<Long, TournamentParticipant> byPlayer) {
        for (int position = 0; position < plan.firstRound().size(); position++) {
            FirstRoundPairing pairing = plan.firstRound().get(position);
            TournamentMatch match = bracket.get(bracketKey(1, position));
            match.setPlayer1(byPlayer.get(pairing.player1().playerId()).getPlayer());
            if (pairing.isBye()) {
                match.setStatus(TournamentMatchStatus.BYE);
                match.setWinner(match.getPlayer1());
                placeInNextMatch(match, bracket.get(bracketKey(2, position / 2)));
            } else {
                match.setPlayer2(byPlayer.get(pairing.player2().playerId()).getPlayer());
            }
        }
    }

    // ------------------------------------------------------------------ results

    @Override
    @Transactional
    public TournamentMatchResponseDTO declareWalkover(Long tournamentId, Long tournamentMatchId,
                                                      TournamentWalkoverRequestDTO dto) {
        Tournament tournament = findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        validateInProgress(tournament);

        TournamentMatch match = tournamentMatchRepository.findById(tournamentMatchId)
                .filter(m -> m.getTournament().getId().equals(tournamentId))
                .orElseThrow(() -> new ResourceNotFoundException("Partido del torneo no encontrado con ID: " + tournamentMatchId));
        if (match.getStatus() != TournamentMatchStatus.SCHEDULED) {
            throw new ConflictException("Solo se puede declarar W.O. en un partido programado y sin resultado");
        }
        if (!match.hasPlayer(dto.getAbsentPlayerId())) {
            throw new InvalidMatchStateException("El jugador ausente no participa en este partido");
        }
        // A W.O. never overwrites a score already reported in the Match module
        if (matchIntegrationPort.hasReportedScore(match.getMatch())) {
            throw new ConflictException("El partido ya tiene un marcador reportado en Match; no se puede declarar W.O.");
        }

        boolean player1Absent = match.getPlayer1().getId().equals(dto.getAbsentPlayerId());
        match.setWinner(player1Absent ? match.getPlayer2() : match.getPlayer1());
        match.setStatus(TournamentMatchStatus.WALKOVER);

        if (match.getMatch() != null) {
            matchIntegrationPort.closeAsWalkover(match.getMatch(), match.getWinner());
        }

        afterMatchFinished(tournament, match);
        return toMatchResponse(tournamentMatchRepository.save(match));
    }

    @Override
    @Transactional
    public TournamentResponseDTO syncMatchResults(Long tournamentId) {
        Tournament tournament = findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        validateInProgress(tournament);

        for (TournamentMatch match : tournamentMatchRepository.findByTournamentIdOrderByIdAsc(tournamentId)) {
            if (match.getStatus() == TournamentMatchStatus.SCHEDULED && match.getMatch() != null) {
                matchIntegrationPort.findConfirmedOutcome(match.getMatch())
                        .ifPresent(outcome -> applyOutcome(tournament, match, outcome));
            }
        }
        return toResponse(tournament);
    }

    // Runs in its own transaction because it is called after the Match transaction has committed
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyConfirmedMatch(Long matchId) {
        tournamentMatchRepository.findByMatchId(matchId)
                .filter(match -> match.getStatus() == TournamentMatchStatus.SCHEDULED)
                .ifPresent(match -> matchIntegrationPort.findConfirmedOutcome(match.getMatch())
                        .ifPresent(outcome -> applyOutcome(match.getTournament(), match, outcome)));
    }

    private void applyOutcome(Tournament tournament, TournamentMatch match, MatchOutcome outcome) {
        // The real Match must have been played by exactly the two players of this tournament match
        Long player1Id = match.getPlayer1().getId();
        Long player2Id = match.getPlayer2().getId();
        boolean sameOrder = player1Id.equals(outcome.player1Id()) && player2Id.equals(outcome.player2Id());
        boolean swapped = player1Id.equals(outcome.player2Id()) && player2Id.equals(outcome.player1Id());
        if (!sameOrder && !swapped) {
            throw new ConflictException("Resultado inválido: los jugadores del Match no coinciden con el partido " + match.getId());
        }
        if (!match.hasPlayer(outcome.winnerPlayerId())) {
            throw new ConflictException("Resultado inválido: el ganador no participa en el partido " + match.getId());
        }

        // Express sets and points from the tournament match's point of view
        int sets1 = sameOrder ? outcome.setsPlayer1() : outcome.setsPlayer2();
        int sets2 = sameOrder ? outcome.setsPlayer2() : outcome.setsPlayer1();
        int points1 = sameOrder ? outcome.pointsPlayer1() : outcome.pointsPlayer2();
        int points2 = sameOrder ? outcome.pointsPlayer2() : outcome.pointsPlayer1();

        boolean player1Won = player1Id.equals(outcome.winnerPlayerId());
        int winnerSets = player1Won ? sets1 : sets2;
        int loserSets = player1Won ? sets2 : sets1;
        if (winnerSets <= loserSets) {
            throw new ConflictException("Resultado inválido: el ganador debe tener más sets que el perdedor en el partido " + match.getId());
        }

        match.setSetsPlayer1(sets1);
        match.setSetsPlayer2(sets2);
        match.setPointsPlayer1(points1);
        match.setPointsPlayer2(points2);
        match.setWinner(player1Won ? match.getPlayer1() : match.getPlayer2());
        match.setStatus(TournamentMatchStatus.COMPLETED);

        afterMatchFinished(tournament, match);
        tournamentMatchRepository.save(match);
    }

    // The bracket updates itself: the knockout winner moves to the next round, or wins the tournament
    private void afterMatchFinished(Tournament tournament, TournamentMatch match) {
        if (match.getStage() != TournamentStage.KNOCKOUT) {
            return;
        }

        Optional<TournamentMatch> next = tournamentMatchRepository.findByTournamentIdAndStageAndRoundAndBracketPosition(
                tournament.getId(), TournamentStage.KNOCKOUT, match.getRound() + 1, match.getBracketPosition() / 2);

        if (next.isEmpty()) {
            tournament.setWinner(match.getWinner());
            tournament.setStatus(TournamentStatus.FINISHED);
            tournamentRepository.save(tournament);
            return;
        }

        TournamentMatch nextMatch = next.get();
        placeInNextMatch(match, nextMatch);
        if (nextMatch.getPlayer1() != null && nextMatch.getPlayer2() != null
                && nextMatch.getStatus() == TournamentMatchStatus.PENDING_PLAYERS) {
            schedule(tournament, nextMatch);
        }
        tournamentMatchRepository.save(nextMatch);
    }

    private void placeInNextMatch(TournamentMatch finished, TournamentMatch next) {
        if (next == null) {
            return;
        }
        if (finished.getBracketPosition() % 2 == 0) {
            next.setPlayer1(finished.getWinner());
        } else {
            next.setPlayer2(finished.getWinner());
        }
    }

    private void schedule(Tournament tournament, TournamentMatch match) {
        if (match.getPlayer1().getId().equals(match.getPlayer2().getId())) {
            throw new ConflictException("Un jugador no puede enfrentarse a sí mismo");
        }
        // Never create a second real Match for the same tournament match
        if (match.getMatch() == null) {
            match.setMatch(matchIntegrationPort.createMatch(match.getPlayer1(), match.getPlayer2(), tournament.getMatchFormat()));
        }
        match.setStatus(TournamentMatchStatus.SCHEDULED);
    }

    // ------------------------------------------------------------------ standings helpers

    private record GroupData(int groupNumber, List<TournamentParticipant> participants,
                             GroupStandings standings, boolean completed) {
    }

    private List<GroupData> calculateGroups(Tournament tournament) {
        List<TournamentParticipant> participants = participantRepository.findByTournamentIdOrderBySeedAsc(tournament.getId());
        List<TournamentMatch> groupMatches = tournamentMatchRepository.findByTournamentIdAndStage(tournament.getId(), TournamentStage.GROUP);

        Map<Integer, List<TournamentParticipant>> byGroup = participants.stream()
                .filter(p -> p.getGroupNumber() != null)
                .collect(Collectors.groupingBy(TournamentParticipant::getGroupNumber, TreeMap::new, Collectors.toList()));

        List<GroupData> groups = new ArrayList<>();
        byGroup.forEach((groupNumber, members) -> {
            List<TournamentMatch> matches = groupMatches.stream()
                    .filter(m -> groupNumber.equals(m.getGroupNumber()))
                    .toList();
            List<GroupMatchResult> results = matches.stream()
                    .filter(m -> m.getStatus().isFinished())
                    .map(this::toGroupResult)
                    .toList();
            Map<Long, Integer> manualOrder = members.stream()
                    .filter(p -> p.getTiebreakOrder() != null)
                    .collect(Collectors.toMap(p -> p.getPlayer().getId(), TournamentParticipant::getTiebreakOrder));

            GroupStandings standings = standingsCalculator.calculate(
                    members.stream().map(p -> p.getPlayer().getId()).toList(), results, manualOrder);
            boolean completed = matches.stream().allMatch(m -> m.getStatus().isFinished());
            groups.add(new GroupData(groupNumber, members, standings, completed));
        });
        return groups;
    }

    private GroupMatchResult toGroupResult(TournamentMatch match) {
        return new GroupMatchResult(
                match.getPlayer1().getId(),
                match.getPlayer2().getId(),
                match.getWinner().getId(),
                match.getStatus() == TournamentMatchStatus.WALKOVER,
                zeroIfNull(match.getSetsPlayer1()),
                zeroIfNull(match.getSetsPlayer2()),
                zeroIfNull(match.getPointsPlayer1()),
                zeroIfNull(match.getPointsPlayer2()));
    }

    private List<GroupStandingsResponseDTO> toStandingsResponse(List<GroupData> groups) {
        return groups.stream().map(group -> {
            Map<Long, Player> players = group.participants().stream()
                    .collect(Collectors.toMap(p -> p.getPlayer().getId(), TournamentParticipant::getPlayer));
            boolean blocked = group.standings().hasBlockingTie(QUALIFIERS_PER_GROUP);

            List<GroupStandingRowDTO> rows = group.standings().rows().stream()
                    .map(row -> GroupStandingRowDTO.builder()
                            .position(row.position())
                            .player(modelMapper.map(players.get(row.playerId()), PlayerSummaryDTO.class))
                            .played(row.played())
                            .wins(row.wins())
                            .losses(row.losses())
                            .setDifference(row.setsWon() - row.setsLost())
                            .pointDifference(row.pointsWon() - row.pointsLost())
                            .unresolvedTie(row.unresolvedTie())
                            .qualified(group.completed() && !blocked && row.position() <= QUALIFIERS_PER_GROUP)
                            .build())
                    .toList();

            return GroupStandingsResponseDTO.builder()
                    .groupNumber(group.groupNumber())
                    .completed(group.completed())
                    .blockedByTie(blocked)
                    .standings(rows)
                    .unresolvedTies(group.standings().unresolvedTies())
                    .build();
        }).toList();
    }

    // ------------------------------------------------------------------ validation helpers

    private Tournament findTournament(Long tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado con ID: " + tournamentId));    }

    // Tournaments are managed by the current admin of the organizing club
    private Tournament findManagedTournament(Long tournamentId, Long actingPlayerId) {
        Tournament tournament = findTournament(tournamentId);
        validateClubAdmin(tournament.getClub(), actingPlayerId);
        return tournament;
    }

    private void validateClubAdmin(Club club, Long actingPlayerId) {
        if (!club.getAdmin().getId().equals(actingPlayerId)) {
            throw new UnauthorizedActionException("Solo el administrador del club organizador puede gestionar sus torneos");
        }
    }

    private void validateStatus(Tournament tournament, TournamentStatus expected, String message) {
        if (tournament.getStatus() != expected) {
            throw new ConflictException(message);
        }
    }

    private void validateInProgress(Tournament tournament) {
        if (tournament.getStatus() != TournamentStatus.GROUP_STAGE && tournament.getStatus() != TournamentStatus.KNOCKOUT_STAGE) {
            throw new ConflictException("El torneo no está en juego");
        }
    }

    // Returns the reason why a player cannot take part, or null if eligible
    private String eligibilityError(Tournament tournament, Player player) {
        if (player.getStatus() != PlayerStatus.ACTIVE) {
            return "Solo los jugadores ACTIVE pueden participar en torneos (" + player.getName() + " está " + player.getStatus() + ")";
        }
        if (tournament.getType() == TournamentType.INTERNAL
                && !clubMembershipService.isActiveMember(tournament.getClub().getId(), player.getId())) {
            return "En un torneo INTERNAL solo participan miembros activos del club (" + player.getName() + " no lo es)";
        }
        return null;
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

    private Map<Long, TournamentParticipant> indexByPlayer(List<TournamentParticipant> participants) {
        return participants.stream().collect(Collectors.toMap(p -> p.getPlayer().getId(), Function.identity()));
    }

    private String bracketKey(int round, int position) {
        return round + "-" + position;
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    // ------------------------------------------------------------------ mapping

    private TournamentResponseDTO toResponse(Tournament tournament) {
        return modelMapper.map(tournament, TournamentResponseDTO.class);
    }

    private TournamentParticipantResponseDTO toParticipantResponse(TournamentParticipant participant) {
        return modelMapper.map(participant, TournamentParticipantResponseDTO.class);
    }

    private TournamentMatchResponseDTO toMatchResponse(TournamentMatch match) {
        return modelMapper.map(match, TournamentMatchResponseDTO.class);
    }
}

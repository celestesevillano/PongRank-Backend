package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.Tournament.TournamentMatch;
import org.example.pongrankbackend.Tournament.TournamentMatchStatus;
import org.example.pongrankbackend.Tournament.TournamentParticipant;
import org.example.pongrankbackend.Tournament.TournamentStage;
import org.example.pongrankbackend.Tournament.TournamentStatus;
import org.example.pongrankbackend.Tournament.dto.GroupStandingsResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentOrderedPlayersRequestDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.example.pongrankbackend.Tournament.engine.GroupDistributor;
import org.example.pongrankbackend.Tournament.engine.GroupStandingsCalculator.StandingRow;
import org.example.pongrankbackend.Tournament.engine.KnockoutBracketBuilder.BracketPlan;
import org.example.pongrankbackend.Tournament.engine.KnockoutBracketBuilder.FirstRoundPairing;
import org.example.pongrankbackend.Tournament.engine.KnockoutBracketBuilder.Qualifier;
import org.example.pongrankbackend.Tournament.engine.KnockoutBracketBuilder;
import org.example.pongrankbackend.Tournament.engine.RoundRobinScheduler;
import org.example.pongrankbackend.Tournament.repository.TournamentMatchRepository;
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
public class TournamentBracketServiceImpl implements TournamentBracketService {

    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final GroupDistributor groupDistributor;
    private final RoundRobinScheduler roundRobinScheduler;
    private final KnockoutBracketBuilder bracketBuilder;
    private final TournamentAccessPolicy accessPolicy;
    private final TournamentMapper tournamentMapper;
    private final TournamentGroupStandings groupStandings;
    private final TournamentMatchScheduler matchScheduler;

    public TournamentBracketServiceImpl(TournamentRepository tournamentRepository,
                                        TournamentParticipantRepository participantRepository,
                                        TournamentMatchRepository tournamentMatchRepository,
                                        GroupDistributor groupDistributor,
                                        RoundRobinScheduler roundRobinScheduler,
                                        KnockoutBracketBuilder bracketBuilder,
                                        TournamentAccessPolicy accessPolicy,
                                        TournamentMapper tournamentMapper,
                                        TournamentGroupStandings groupStandings,
                                        TournamentMatchScheduler matchScheduler) {
        this.tournamentRepository = tournamentRepository;
        this.participantRepository = participantRepository;
        this.tournamentMatchRepository = tournamentMatchRepository;
        this.groupDistributor = groupDistributor;
        this.roundRobinScheduler = roundRobinScheduler;
        this.bracketBuilder = bracketBuilder;
        this.accessPolicy = accessPolicy;
        this.tournamentMapper = tournamentMapper;
        this.groupStandings = groupStandings;
        this.matchScheduler = matchScheduler;
    }

    @Override
    @Transactional
    public TournamentResponseDTO startTournament(Long tournamentId) {
        Tournament tournament = accessPolicy.findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        accessPolicy.validateStatus(tournament, TournamentStatus.OPEN, "El torneo ya fue iniciado");

        List<TournamentParticipant> participants = participantRepository.findByTournamentIdOrderBySeedAsc(tournamentId);
        validateDistributable(participants);
        validateAllEligible(tournament, participants);

        Map<Long, TournamentParticipant> byPlayer = TournamentParticipants.indexByPlayer(participants);
        List<List<Long>> groups = groupDistributor.distribute(participants.stream().map(p -> p.getPlayer().getId()).toList());
        List<TournamentMatch> fixtures = buildGroupStageFixtures(tournament, groups, byPlayer);

        participantRepository.saveAll(participants);
        tournamentMatchRepository.saveAll(fixtures);
        tournament.setStatus(TournamentStatus.GROUP_STAGE);
        return tournamentMapper.toResponse(tournamentRepository.save(tournament));
    }

    private void validateDistributable(List<TournamentParticipant> participants) {
        if (!groupDistributor.canDistribute(participants.size())) {
            throw new ConflictException("Se necesitan al menos 3 participantes y una cantidad que permita formar grupos de 3 o 4 jugadores (con "
                    + participants.size() + " no es posible)");
        }
    }

    private void validateAllEligible(Tournament tournament, List<TournamentParticipant> participants) {
        List<String> ineligible = participants.stream()
                .map(p -> accessPolicy.eligibilityError(tournament, p.getPlayer()) == null ? null : p.getPlayer().getName())
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
                matchScheduler.schedule(tournament, fixture);
                fixtures.add(fixture);
            }
        }
        return fixtures;
    }

    @Override
    @Transactional
    public List<GroupStandingsResponseDTO> resolveGroupTie(Long tournamentId, Integer groupNumber,
                                                           TournamentOrderedPlayersRequestDTO dto) {
        Tournament tournament = accessPolicy.findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        accessPolicy.validateStatus(tournament, TournamentStatus.GROUP_STAGE, "Los empates solo se resuelven durante la fase de grupos");

        GroupData group = groupStandings.calculateGroups(tournament).stream()
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

        Map<Long, TournamentParticipant> byPlayer = TournamentParticipants.indexByPlayer(group.participants());
        for (int i = 0; i < ordered.size(); i++) {
            byPlayer.get(ordered.get(i)).setTiebreakOrder(i + 1);
        }
        participantRepository.saveAll(group.participants());

        return groupStandings.toStandingsResponse(groupStandings.calculateGroups(tournament));
    }

    @Override
    @Transactional
    public TournamentResponseDTO generateKnockout(Long tournamentId, boolean allowSameGroupMatches) {
        Tournament tournament = accessPolicy.findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        accessPolicy.validateStatus(tournament, TournamentStatus.GROUP_STAGE, "La llave solo se genera al terminar la fase de grupos");

        List<GroupData> groups = groupStandings.calculateGroups(tournament);
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
                .forEach(m -> matchScheduler.schedule(tournament, m));

        tournamentMatchRepository.saveAll(bracket.values());
        tournament.setStatus(TournamentStatus.KNOCKOUT_STAGE);
        return tournamentMapper.toResponse(tournamentRepository.save(tournament));
    }

    private void validateGroupsCompleted(List<GroupData> groups) {
        if (groups.stream().anyMatch(g -> !g.completed())) {
            throw new ConflictException("Todos los partidos de la fase de grupos deben estar finalizados");
        }
    }

    private void validateNoBlockingTies(List<GroupData> groups) {
        List<Integer> blockedGroups = groups.stream()
                .filter(g -> g.standings().hasBlockingTie(TournamentGroupStandings.QUALIFIERS_PER_GROUP))
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
            byPlayer.putAll(TournamentParticipants.indexByPlayer(group.participants()));
            for (StandingRow row : group.standings().rows()) {
                if (row.position() > TournamentGroupStandings.QUALIFIERS_PER_GROUP) {
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
                matchScheduler.placeInNextMatch(match, bracket.get(bracketKey(2, position / 2)));
            } else {
                match.setPlayer2(byPlayer.get(pairing.player2().playerId()).getPlayer());
            }
        }
    }

    private String bracketKey(int round, int position) {
        return round + "-" + position;
    }
}

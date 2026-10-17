package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.Tournament.TournamentMatch;
import org.example.pongrankbackend.Tournament.TournamentMatchStatus;
import org.example.pongrankbackend.Tournament.TournamentParticipant;
import org.example.pongrankbackend.Tournament.TournamentStage;
import org.example.pongrankbackend.Tournament.dto.GroupStandingRowDTO;
import org.example.pongrankbackend.Tournament.dto.GroupStandingsResponseDTO;
import org.example.pongrankbackend.Tournament.engine.GroupStandingsCalculator.GroupMatchResult;
import org.example.pongrankbackend.Tournament.engine.GroupStandingsCalculator.GroupStandings;
import org.example.pongrankbackend.Tournament.engine.GroupStandingsCalculator;
import org.example.pongrankbackend.Tournament.repository.TournamentMatchRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentParticipantRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class TournamentGroupStandings {

    public static final int QUALIFIERS_PER_GROUP = 2;

    private final TournamentParticipantRepository participantRepository;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final GroupStandingsCalculator standingsCalculator;
    private final ModelMapper modelMapper;

    public TournamentGroupStandings(TournamentParticipantRepository participantRepository,
                                    TournamentMatchRepository tournamentMatchRepository,
                                    GroupStandingsCalculator standingsCalculator,
                                    ModelMapper modelMapper) {
        this.participantRepository = participantRepository;
        this.tournamentMatchRepository = tournamentMatchRepository;
        this.standingsCalculator = standingsCalculator;
        this.modelMapper = modelMapper;
    }

    public List<GroupData> calculateGroups(Tournament tournament) {
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

    public List<GroupStandingsResponseDTO> toStandingsResponse(List<GroupData> groups) {
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

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }
}

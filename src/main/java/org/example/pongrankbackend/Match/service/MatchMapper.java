package org.example.pongrankbackend.Match.service;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.dto.*;
import org.example.pongrankbackend.MatchSet.dto.MatchSetResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MatchMapper {

    private final ModelMapper modelMapper;

    public MatchMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public MatchResponseDTO toResponseDTO(Match match) {
        PlayerSummaryDTO p1 = modelMapper.map(match.getPlayer1(), PlayerSummaryDTO.class);
        PlayerSummaryDTO p2 = match.getPlayer2() != null ? modelMapper.map(match.getPlayer2(), PlayerSummaryDTO.class) : null;

        Long commId = match.getCommunity() != null ? match.getCommunity().getId() : null;
        String commName = match.getCommunity() != null ? match.getCommunity().getName() : null;

        Long tourId = match.getTournament() != null ? match.getTournament().getId() : null;
        String tourName = match.getTournament() != null ? match.getTournament().getName() : null;

        String scoreSummary = null;
        Long winnerId = null;

        if (match.getWinner() != null) {
            winnerId = match.getWinner().getId();
        }

        if (match.getSets() != null && !match.getSets().isEmpty()) {
            long w1 = match.getSets().stream().filter(s -> s.getScorePlayer1() > s.getScorePlayer2()).count();
            long w2 = match.getSets().stream().filter(s -> s.getScorePlayer2() > s.getScorePlayer1()).count();
            scoreSummary = w1 + " - " + w2;
            if (winnerId == null && match.getStatus() == MatchStatus.CONFIRMED && match.getPlayer2() != null) {
                winnerId = w1 > w2 ? match.getPlayer1().getId() : match.getPlayer2().getId();
            }
        } else if (match.getStatus() == MatchStatus.WALKOVER) {
            scoreSummary = "W.O.";
        }

        return MatchResponseDTO.builder()
                .id(match.getId())
                .player1(p1)
                .player2(p2)
                .communityId(commId)
                .communityName(commName)
                .tournamentId(tourId)
                .tournamentName(tourName)
                .format(match.getFormat())
                .matchType(match.getMatchType())
                .status(match.getStatus())
                .latitude(match.getLatitude())
                .longitude(match.getLongitude())
                .scoreSummary(scoreSummary)
                .winnerId(winnerId)
                .ratingDeltaP1(match.getRatingDeltaP1())
                .ratingDeltaP2(match.getRatingDeltaP2())
                .createdAt(match.getCreatedAt())
                .confirmedAt(match.getConfirmedAt())
                .build();
    }

    public MatchDetailResponseDTO toDetailResponseDTO(Match match) {
        MatchResponseDTO base = toResponseDTO(match);

        List<MatchSetResponseDTO> setDTOs = new ArrayList<>();
        if (match.getSets() != null) {
            setDTOs = match.getSets().stream()
                    .map(s -> MatchSetResponseDTO.builder()
                            .id(s.getId())
                            .setNumber(s.getSetNumber())
                            .scorePlayer1(s.getScorePlayer1())
                            .scorePlayer2(s.getScorePlayer2())
                            .winnerPlayerNumber(s.getScorePlayer1() > s.getScorePlayer2() ? 1 : 2)
                            .build())
                    .toList();
        }

        return MatchDetailResponseDTO.builder()
                .id(base.getId())
                .player1(base.getPlayer1())
                .player2(base.getPlayer2())
                .communityId(base.getCommunityId())
                .communityName(base.getCommunityName())
                .tournamentId(base.getTournamentId())
                .tournamentName(base.getTournamentName())
                .format(base.getFormat())
                .matchType(base.getMatchType())
                .status(base.getStatus())
                .latitude(base.getLatitude())
                .longitude(base.getLongitude())
                .scoreSummary(base.getScoreSummary())
                .winnerId(base.getWinnerId())
                .ratingDeltaP1(base.getRatingDeltaP1())
                .ratingDeltaP2(base.getRatingDeltaP2())
                .disputeReason(match.getDisputeReason())
                .createdAt(base.getCreatedAt())
                .confirmedAt(base.getConfirmedAt())
                .sets(setDTOs)
                .build();
    }
}

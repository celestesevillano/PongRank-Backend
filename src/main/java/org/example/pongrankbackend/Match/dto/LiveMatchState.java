package org.example.pongrankbackend.Match.dto;

import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.MatchSet.dto.MatchSetRequestDTO;

import java.util.List;

public record LiveMatchState(
        long revision, MatchStatus status, boolean player1Ready, boolean player2Ready,
        List<MatchSetRequestDTO> completedSets, int scorePlayer1, int scorePlayer2,
        Integer lastPointBy, boolean finished
) {}

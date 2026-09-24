package org.example.pongrankbackend.Tournament.integration;

// Confirmed result of a Match, expressed from the point of view of Match.player1 / Match.player2.
// player1Id / player2Id let Tournament check that the Match was played by the players of the tournament match.
public record MatchOutcome(Long player1Id, Long player2Id, Long winnerPlayerId,
                           int setsPlayer1, int setsPlayer2, int pointsPlayer1, int pointsPlayer2) {
}

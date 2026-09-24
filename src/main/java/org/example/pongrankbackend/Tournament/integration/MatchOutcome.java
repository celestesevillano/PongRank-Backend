package org.example.pongrankbackend.Tournament.integration;

// Confirmed result of a Match, expressed from the point of view of Match.player1 / Match.player2
public record MatchOutcome(Long winnerPlayerId, int setsPlayer1, int setsPlayer2, int pointsPlayer1, int pointsPlayer2) {
}

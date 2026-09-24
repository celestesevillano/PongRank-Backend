package org.example.pongrankbackend.Tournament.integration;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Player.Player;

import java.util.Optional;

/*
 * Minimum contract Tournament needs from the Match module (owner: Adriana).
 * Tournament never validates set scores, deuce or service rules: that belongs to Match.
 */
public interface MatchIntegrationPort {

    // Creates the real match that will be played between both players with the tournament's format
    Match createMatch(Player player1, Player player2, MatchFormat format);

    // Returns the result only when the Match module considers the match CONFIRMED
    Optional<MatchOutcome> findConfirmedOutcome(Match match);
}

package org.example.pongrankbackend.Match.rating;

import org.example.pongrankbackend.Match.Match;

public interface Glicko2Service {

    void applyMatchRatingUpdate(Match match, boolean player1Won);
}

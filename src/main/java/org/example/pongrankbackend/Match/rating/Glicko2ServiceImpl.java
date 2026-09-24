package org.example.pongrankbackend.Match.rating;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Glicko2ServiceImpl implements Glicko2Service {

    private final Glicko2Calculator glicko2Calculator;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;

    public Glicko2ServiceImpl(Glicko2Calculator glicko2Calculator,
                              PlayerRepository playerRepository,
                              MatchRepository matchRepository) {
        this.glicko2Calculator = glicko2Calculator;
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
    }

    @Override
    @Transactional
    public void applyMatchRatingUpdate(Match match, boolean player1Won) {
        Player p1 = match.getPlayer1();
        Player p2 = match.getPlayer2();

        if (p1 == null || p2 == null) {
            return;
        }

        double scoreP1 = player1Won ? 1.0 : 0.0;
        double scoreP2 = player1Won ? 0.0 : 1.0;

        // Cálculo para Jugador 1
        Glicko2Result resultP1 = glicko2Calculator.calculate(
                p1.getRatingGlicko(),
                p1.getRatingDeviation(),
                p1.getVolatility(),
                p2.getRatingGlicko(),
                p2.getRatingDeviation(),
                scoreP1
        );

        // Cálculo para Jugador 2
        Glicko2Result resultP2 = glicko2Calculator.calculate(
                p2.getRatingGlicko(),
                p2.getRatingDeviation(),
                p2.getVolatility(),
                p1.getRatingGlicko(),
                p1.getRatingDeviation(),
                scoreP2
        );

        // Actualizar jugador 1
        p1.setRatingGlicko(resultP1.getNewRating());
        p1.setRatingDeviation(resultP1.getNewRatingDeviation());
        p1.setVolatility(resultP1.getNewVolatility());
        playerRepository.save(p1);

        // Actualizar jugador 2
        p2.setRatingGlicko(resultP2.getNewRating());
        p2.setRatingDeviation(resultP2.getNewRatingDeviation());
        p2.setVolatility(resultP2.getNewVolatility());
        playerRepository.save(p2);

        // Registrar deltas en el partido
        match.setRatingDeltaP1(resultP1.getRatingDelta());
        match.setRatingDeltaP2(resultP2.getRatingDelta());
        matchRepository.save(match);
    }
}

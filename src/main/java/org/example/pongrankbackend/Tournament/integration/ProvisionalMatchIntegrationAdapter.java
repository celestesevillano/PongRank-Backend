package org.example.pongrankbackend.Tournament.integration;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.dto.MatchDetailResponseDTO;
import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.Match.service.MatchService;
import org.example.pongrankbackend.MatchSet.dto.MatchSetResponseDTO;
import org.example.pongrankbackend.Player.Player;
import org.springframework.stereotype.Component;

import java.util.Optional;

/*
 * Adapter between Tournament and the Match module (owner: Adriana).
 *
 * RESULTS (integrated): the winner, the sets and the CONFIRMED status come from MatchService.getMatchById.
 * Score validation (sets to 11, deuce, BO3/BO5/BO7) happens in Match when players use its submit/confirm endpoints.
 *
 * CREATION (PROVISIONAL): MatchService.createMatch only accepts FRIEND, COMMUNITY or LOCATION matches
 * (FRIEND requires a friendship), so tournament matches cannot be created through it yet. Until the Match
 * module offers a tournament contract, the Match row is saved with its repository. Agreed with Adriana: Match will
 * allow a match between two players registered in the same tournament (without friendship, community or location);
 * FRIEND/COMMUNITY/LOCATION stay as they are and no MatchType.TOURNAMENT is added from here.
 * Note: until that contract exists, the provisional row keeps Match's default matchType (FRIEND).
 * When the contract is published, only createMatch changes; the rest of Tournament uses this port.
 */
@Component
public class ProvisionalMatchIntegrationAdapter implements MatchIntegrationPort {

    private final MatchRepository matchRepository;
    private final MatchService matchService;

    public ProvisionalMatchIntegrationAdapter(MatchRepository matchRepository, MatchService matchService) {
        this.matchRepository = matchRepository;
        this.matchService = matchService;
    }

    @Override
    public Match createMatch(Player player1, Player player2, MatchFormat format) {
        Match match = Match.builder()
                .player1(player1)
                .player2(player2)
                .format(format)
                .status(MatchStatus.CREATED)
                .build();
        return matchRepository.save(match);
    }

    @Override
    public Optional<MatchOutcome> findConfirmedOutcome(Match match) {
        if (match == null || match.getId() == null) {
            return Optional.empty();
        }

        MatchDetailResponseDTO detail = matchService.getMatchById(match.getId());
        if (detail.getStatus() != MatchStatus.CONFIRMED || detail.getWinnerId() == null) {
            return Optional.empty();
        }

        int setsPlayer1 = 0;
        int setsPlayer2 = 0;
        int pointsPlayer1 = 0;
        int pointsPlayer2 = 0;
        for (MatchSetResponseDTO set : detail.getSets()) {
            pointsPlayer1 += set.getScorePlayer1();
            pointsPlayer2 += set.getScorePlayer2();
            if (Integer.valueOf(1).equals(set.getWinnerPlayerNumber())) {
                setsPlayer1++;
            } else {
                setsPlayer2++;
            }
        }
        return Optional.of(new MatchOutcome(detail.getPlayer1().getId(), detail.getPlayer2().getId(), detail.getWinnerId(),
                setsPlayer1, setsPlayer2, pointsPlayer1, pointsPlayer2));
    }

    @Override
    public boolean hasReportedScore(Match match) {
        if (match == null || match.getId() == null) {
            return false;
        }
        MatchStatus status = matchService.getMatchById(match.getId()).getStatus();
        return status != MatchStatus.CREATED && status != MatchStatus.CANCELLED;
    }
}

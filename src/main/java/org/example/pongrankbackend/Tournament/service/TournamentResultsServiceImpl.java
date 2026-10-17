package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.Tournament.TournamentMatch;
import org.example.pongrankbackend.Tournament.TournamentMatchStatus;
import org.example.pongrankbackend.Tournament.TournamentStage;
import org.example.pongrankbackend.Tournament.TournamentStatus;
import org.example.pongrankbackend.Tournament.dto.TournamentMatchResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentResponseDTO;
import org.example.pongrankbackend.Tournament.dto.TournamentWalkoverRequestDTO;
import org.example.pongrankbackend.Tournament.integration.MatchIntegrationPort;
import org.example.pongrankbackend.Tournament.integration.MatchOutcome;
import org.example.pongrankbackend.Tournament.repository.TournamentMatchRepository;
import org.example.pongrankbackend.Tournament.repository.TournamentRepository;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.InvalidMatchStateException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class TournamentResultsServiceImpl implements TournamentResultsService {

    private final TournamentRepository tournamentRepository;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final MatchIntegrationPort matchIntegrationPort;
    private final TournamentAccessPolicy accessPolicy;
    private final TournamentMapper tournamentMapper;
    private final TournamentMatchScheduler matchScheduler;

    public TournamentResultsServiceImpl(TournamentRepository tournamentRepository,
                                        TournamentMatchRepository tournamentMatchRepository,
                                        MatchIntegrationPort matchIntegrationPort,
                                        TournamentAccessPolicy accessPolicy,
                                        TournamentMapper tournamentMapper,
                                        TournamentMatchScheduler matchScheduler) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentMatchRepository = tournamentMatchRepository;
        this.matchIntegrationPort = matchIntegrationPort;
        this.accessPolicy = accessPolicy;
        this.tournamentMapper = tournamentMapper;
        this.matchScheduler = matchScheduler;
    }

    @Override
    @Transactional
    public TournamentMatchResponseDTO declareWalkover(Long tournamentId, Long tournamentMatchId,
                                                      TournamentWalkoverRequestDTO dto) {
        Tournament tournament = accessPolicy.findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        accessPolicy.validateInProgress(tournament);

        TournamentMatch match = tournamentMatchRepository.findById(tournamentMatchId)
                .filter(m -> m.getTournament().getId().equals(tournamentId))
                .orElseThrow(() -> new ResourceNotFoundException("Partido del torneo no encontrado con ID: " + tournamentMatchId));
        if (match.getStatus() != TournamentMatchStatus.SCHEDULED) {
            throw new ConflictException("Solo se puede declarar W.O. en un partido programado y sin resultado");
        }
        if (!match.hasPlayer(dto.getAbsentPlayerId())) {
            throw new InvalidMatchStateException("El jugador ausente no participa en este partido");
        }
        // A W.O. never overwrites a score already reported in the Match module
        if (matchIntegrationPort.hasReportedScore(match.getMatch())) {
            throw new ConflictException("El partido ya tiene un marcador reportado en Match; no se puede declarar W.O.");
        }

        boolean player1Absent = match.getPlayer1().getId().equals(dto.getAbsentPlayerId());
        match.setWinner(player1Absent ? match.getPlayer2() : match.getPlayer1());
        match.setStatus(TournamentMatchStatus.WALKOVER);

        if (match.getMatch() != null) {
            matchIntegrationPort.closeAsWalkover(match.getMatch(), match.getWinner());
        }

        afterMatchFinished(tournament, match);
        return tournamentMapper.toMatchResponse(tournamentMatchRepository.save(match));
    }

    @Override
    @Transactional
    public TournamentResponseDTO syncMatchResults(Long tournamentId) {
        Tournament tournament = accessPolicy.findManagedTournament(tournamentId, SecurityUtils.getRequiredCurrentUserId());
        accessPolicy.validateInProgress(tournament);

        for (TournamentMatch match : tournamentMatchRepository.findByTournamentIdOrderByIdAsc(tournamentId)) {
            if (match.getStatus() == TournamentMatchStatus.SCHEDULED && match.getMatch() != null) {
                matchIntegrationPort.findConfirmedOutcome(match.getMatch())
                        .ifPresent(outcome -> applyOutcome(tournament, match, outcome));
            }
        }
        return tournamentMapper.toResponse(tournament);
    }

    @Override
    @Transactional
    public void applyConfirmedMatch(Long matchId) {
        tournamentMatchRepository.findByMatchId(matchId)
                .filter(match -> match.getStatus() == TournamentMatchStatus.SCHEDULED)
                .ifPresent(match -> matchIntegrationPort.findConfirmedOutcome(match.getMatch())
                        .ifPresent(outcome -> applyOutcome(match.getTournament(), match, outcome)));
    }

    private void applyOutcome(Tournament tournament, TournamentMatch match, MatchOutcome outcome) {
        // The real Match must have been played by exactly the two players of this tournament match
        Long player1Id = match.getPlayer1().getId();
        Long player2Id = match.getPlayer2().getId();
        boolean sameOrder = player1Id.equals(outcome.player1Id()) && player2Id.equals(outcome.player2Id());
        boolean swapped = player1Id.equals(outcome.player2Id()) && player2Id.equals(outcome.player1Id());
        if (!sameOrder && !swapped) {
            throw new ConflictException("Resultado inválido: los jugadores del Match no coinciden con el partido " + match.getId());
        }
        if (!match.hasPlayer(outcome.winnerPlayerId())) {
            throw new ConflictException("Resultado inválido: el ganador no participa en el partido " + match.getId());
        }

        // Express sets and points from the tournament match's point of view
        int sets1 = sameOrder ? outcome.setsPlayer1() : outcome.setsPlayer2();
        int sets2 = sameOrder ? outcome.setsPlayer2() : outcome.setsPlayer1();
        int points1 = sameOrder ? outcome.pointsPlayer1() : outcome.pointsPlayer2();
        int points2 = sameOrder ? outcome.pointsPlayer2() : outcome.pointsPlayer1();

        boolean player1Won = player1Id.equals(outcome.winnerPlayerId());
        int winnerSets = player1Won ? sets1 : sets2;
        int loserSets = player1Won ? sets2 : sets1;
        if (winnerSets <= loserSets) {
            throw new ConflictException("Resultado inválido: el ganador debe tener más sets que el perdedor en el partido " + match.getId());
        }

        match.setSetsPlayer1(sets1);
        match.setSetsPlayer2(sets2);
        match.setPointsPlayer1(points1);
        match.setPointsPlayer2(points2);
        match.setWinner(player1Won ? match.getPlayer1() : match.getPlayer2());
        match.setStatus(TournamentMatchStatus.COMPLETED);

        afterMatchFinished(tournament, match);
        tournamentMatchRepository.save(match);
    }

    // The bracket updates itself: the knockout winner moves to the next round, or wins the tournament
    private void afterMatchFinished(Tournament tournament, TournamentMatch match) {
        if (match.getStage() != TournamentStage.KNOCKOUT) {
            return;
        }

        Optional<TournamentMatch> next = tournamentMatchRepository.findByTournamentIdAndStageAndRoundAndBracketPosition(
                tournament.getId(), TournamentStage.KNOCKOUT, match.getRound() + 1, match.getBracketPosition() / 2);

        if (next.isEmpty()) {
            tournament.setWinner(match.getWinner());
            tournament.setStatus(TournamentStatus.FINISHED);
            tournamentRepository.save(tournament);
            return;
        }

        TournamentMatch nextMatch = next.get();
        matchScheduler.placeInNextMatch(match, nextMatch);
        if (nextMatch.getPlayer1() != null && nextMatch.getPlayer2() != null
                && nextMatch.getStatus() == TournamentMatchStatus.PENDING_PLAYERS) {
            matchScheduler.schedule(tournament, nextMatch);
        }
        tournamentMatchRepository.save(nextMatch);
    }
}

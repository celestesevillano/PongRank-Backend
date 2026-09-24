package org.example.pongrankbackend.Tournament.repository;

import org.example.pongrankbackend.Tournament.TournamentMatch;
import org.example.pongrankbackend.Tournament.TournamentStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentMatchRepository extends JpaRepository<TournamentMatch, Long> {

    List<TournamentMatch> findByTournamentIdOrderByIdAsc(Long tournamentId);

    List<TournamentMatch> findByTournamentIdAndStage(Long tournamentId, TournamentStage stage);

    Optional<TournamentMatch> findByTournamentIdAndStageAndRoundAndBracketPosition(
            Long tournamentId, TournamentStage stage, Integer round, Integer bracketPosition);

    Optional<TournamentMatch> findByMatchId(Long matchId);
}

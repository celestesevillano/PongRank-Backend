package org.example.pongrankbackend.Tournament.repository;

import org.example.pongrankbackend.Tournament.TournamentParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipant, Long> {

    List<TournamentParticipant> findByTournamentIdOrderBySeedAsc(Long tournamentId);

    boolean existsByTournamentIdAndPlayerId(Long tournamentId, Long playerId);

    Optional<TournamentParticipant> findByTournamentIdAndPlayerId(Long tournamentId, Long playerId);
}

package org.example.pongrankbackend.TrainingSession.repository;

import org.example.pongrankbackend.TrainingSession.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {

    List<TrainingSession> findByPlayerIdOrderByCreatedAtDesc(Long playerId);

    Optional<TrainingSession> findTopByPlayerIdOrderByPostureScoreDesc(Long playerId);
}

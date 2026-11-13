package org.example.pongrankbackend.Federation;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FederatedReviewRepository extends JpaRepository<FederatedReview, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select review from FederatedReview review where review.playerId = :playerId")
    Optional<FederatedReview> findForDecision(@Param("playerId") Long playerId);

    boolean existsByPlayerIdAndStatus(Long playerId, FederatedReview.Status status);

    Page<FederatedReview> findByStatus(FederatedReview.Status status, Pageable pageable);
}

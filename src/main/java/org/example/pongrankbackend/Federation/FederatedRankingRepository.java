package org.example.pongrankbackend.Federation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FederatedRankingRepository extends JpaRepository<FederatedRankingEntry, Long> {
    Page<FederatedRankingEntry> findByCategoryIgnoreCaseAndNameContainingIgnoreCase(String category, String name, Pageable pageable);
}

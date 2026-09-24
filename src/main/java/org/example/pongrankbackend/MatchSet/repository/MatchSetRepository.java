package org.example.pongrankbackend.MatchSet.repository;

import org.example.pongrankbackend.MatchSet.MatchSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchSetRepository extends JpaRepository<MatchSet, Long> {

    List<MatchSet> findByMatchIdOrderBySetNumberAsc(Long matchId);

    void deleteByMatchId(Long matchId);
}

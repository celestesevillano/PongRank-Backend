package org.example.pongrankbackend.Match.repository;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.MatchType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("SELECT m FROM Match m " +
           "WHERE m.player1.id = :playerId OR m.player2.id = :playerId " +
           "ORDER BY m.createdAt DESC")
    List<Match> findAllByPlayerId(@Param("playerId") Long playerId);

    @Query("SELECT m FROM Match m " +
           "WHERE (m.player1.id = :playerId OR m.player2.id = :playerId) AND m.status = :status " +
           "ORDER BY m.createdAt DESC")
    List<Match> findAllByPlayerIdAndStatus(@Param("playerId") Long playerId, @Param("status") MatchStatus status);

    @Query("SELECT m FROM Match m " +
           "WHERE m.player1.id = :playerId OR m.player2.id = :playerId")
    Page<Match> findAllByPlayerId(@Param("playerId") Long playerId, Pageable pageable);

    @Query("SELECT m FROM Match m " +
           "WHERE (m.player1.id = :playerId OR m.player2.id = :playerId) AND m.status = :status")
    Page<Match> findAllByPlayerIdAndStatus(@Param("playerId") Long playerId, @Param("status") MatchStatus status, Pageable pageable);

    @Query("SELECT m FROM Match m " +
           "WHERE m.community.id = :communityId AND m.status = :status " +
           "ORDER BY m.createdAt DESC")
    List<Match> findByCommunityIdAndStatus(@Param("communityId") Long communityId, @Param("status") MatchStatus status);

    @Query("SELECT m FROM Match m " +
           "WHERE m.player2 IS NULL AND m.status = :status AND m.matchType = :matchType " +
           "ORDER BY m.createdAt DESC")
    List<Match> findOpenChallenges(@Param("status") MatchStatus status, @Param("matchType") MatchType matchType);

    List<Match> findByTournamentId(Long tournamentId);

    Page<Match> findByTournamentId(Long tournamentId, Pageable pageable);
}

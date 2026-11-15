package org.example.pongrankbackend.Friendship.repository;

import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Friendship.FriendshipStatus;
import org.example.pongrankbackend.Player.Player;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    String ACTIVE_PONGMIGOS = """
            FROM Player p
            WHERE p.status = org.example.pongrankbackend.Player.PlayerStatus.ACTIVE
            AND EXISTS (
                SELECT 1 FROM Friendship f
                WHERE f.status = org.example.pongrankbackend.Friendship.FriendshipStatus.ACCEPTED
                AND ((f.playerA.id = :playerId AND f.playerB.id = p.id)
                  OR (f.playerB.id = :playerId AND f.playerA.id = p.id))
            )
            """;

    @Query("SELECT COUNT(p) " + ACTIVE_PONGMIGOS)
    long countActivePongmigos(@Param("playerId") Long playerId);

    @Query(value = "SELECT p " + ACTIVE_PONGMIGOS, countQuery = "SELECT COUNT(p) " + ACTIVE_PONGMIGOS)
    Page<Player> listActivePongmigos(@Param("playerId") Long playerId, Pageable pageable);

    @Query("SELECT p.id " + ACTIVE_PONGMIGOS + " AND p.id IN :candidateIds")
    Set<Long> findActivePongmigoIdsAmong(@Param("playerId") Long playerId,
                                        @Param("candidateIds") Collection<Long> candidateIds);


    @Query("SELECT f FROM Friendship f " +
           "WHERE (f.playerA.id = :p1 AND f.playerB.id = :p2) " +
           "OR (f.playerA.id = :p2 AND f.playerB.id = :p1)")
    Optional<Friendship> findFriendshipBetween(@Param("p1") Long p1, @Param("p2") Long p2);

    @Query("SELECT f FROM Friendship f " +
           "WHERE f.playerA.id = :playerId OR f.playerB.id = :playerId")
    List<Friendship> findAllByPlayerId(@Param("playerId") Long playerId);

    @Query("SELECT f FROM Friendship f " +
           "WHERE (f.playerA.id = :playerId OR f.playerB.id = :playerId) AND f.status = :status")
    List<Friendship> findAllByPlayerIdAndStatus(@Param("playerId") Long playerId, @Param("status") FriendshipStatus status);

    List<Friendship> findByPlayerBIdAndStatus(Long playerBId, FriendshipStatus status);

    List<Friendship> findByPlayerAIdAndStatus(Long playerAId, FriendshipStatus status);
}

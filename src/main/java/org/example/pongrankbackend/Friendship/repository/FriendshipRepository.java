package org.example.pongrankbackend.Friendship.repository;

import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Friendship.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f " +
           "WHERE (f.playerA.id = :p1 AND f.playerB.id = :p2) " +
           "OR (f.playerA.id = :p2 AND f.playerB.id = :p1)")
    boolean existsFriendshipBetween(@Param("p1") Long p1, @Param("p2") Long p2);

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
}

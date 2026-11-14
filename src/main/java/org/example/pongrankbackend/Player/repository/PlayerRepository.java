package org.example.pongrankbackend.Player.repository;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Player p WHERE p.id = :playerId")
    Optional<Player> lockForSpaceAllocation(@Param("playerId") Long playerId);

    // El término ya llega sin comodines (%, _) desde el servicio
    @Query("SELECT p FROM Player p WHERE p.status = :status AND "
            + "(LOWER(p.name) LIKE LOWER(CONCAT('%', :term, '%')) "
            + "OR LOWER(p.username) LIKE LOWER(CONCAT('%', :term, '%')))")
    Page<Player> search(@Param("status") PlayerStatus status, @Param("term") String term, Pageable pageable);

    Optional<Player> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);
}

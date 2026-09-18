package org.example.pongrankbackend.Player.repository;

import org.example.pongrankbackend.Player.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByEmail(String email);

    boolean existsByEmail(String email);
}

package org.example.pongrankbackend.Player.repository;

import org.example.pongrankbackend.Player.PlayerPresentation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerPresentationRepository extends JpaRepository<PlayerPresentation, Long> {
}

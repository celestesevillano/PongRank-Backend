package org.example.pongrankbackend.Tournament.repository;

import org.example.pongrankbackend.Tournament.Tournament;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    Optional<Tournament> findByClubIdAndPublicationKey(Long clubId, String publicationKey);

    Page<Tournament> findByClubId(Long clubId, Pageable pageable);
}

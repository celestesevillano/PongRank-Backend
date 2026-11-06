package org.example.pongrankbackend.ClubAnnouncement.repository;

import org.example.pongrankbackend.ClubAnnouncement.ClubAnnouncement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClubAnnouncementRepository extends JpaRepository<ClubAnnouncement, Long> {

    Page<ClubAnnouncement> findByClubId(Long clubId, Pageable pageable);
}

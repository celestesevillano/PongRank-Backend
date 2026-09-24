package org.example.pongrankbackend.Club.repository;

import org.example.pongrankbackend.Club.ClubReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ClubReviewRepository extends JpaRepository<ClubReview, Long> {

    Page<ClubReview> findByClubId(Long clubId, Pageable pageable);
}

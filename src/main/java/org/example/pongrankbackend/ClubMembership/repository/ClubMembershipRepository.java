package org.example.pongrankbackend.ClubMembership.repository;

import org.example.pongrankbackend.ClubMembership.ClubMembership;
import org.example.pongrankbackend.ClubMembership.ClubMembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface ClubMembershipRepository extends JpaRepository<ClubMembership, Long> {

    boolean existsByPlayerIdAndClubIdAndStatusIn(Long playerId, Long clubId, Collection<ClubMembershipStatus> statuses);

    long countByPlayerIdAndStatusIn(Long playerId, Collection<ClubMembershipStatus> statuses);

    Optional<ClubMembership> findByPlayerIdAndClubIdAndStatus(Long playerId, Long clubId, ClubMembershipStatus status);

    Page<ClubMembership> findByClubIdAndStatus(Long clubId, ClubMembershipStatus status, Pageable pageable);

    Page<ClubMembership> findByPlayerId(Long playerId, Pageable pageable);
}

package org.example.pongrankbackend.Club.repository;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.ClubStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.Collection;
import java.util.List;

@Repository
public interface ClubRepository extends JpaRepository<Club, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Club c where c.id = :id")
    Optional<Club> findByIdForUpdate(@Param("id") Long id);

    @Query("select c.id from Club c where c.status in :statuses")
    List<Long> findLifecycleIds(@Param("statuses") Collection<ClubStatus> statuses);

    Page<Club> findByStatus(ClubStatus status, Pageable pageable);

    Page<Club> findByStatusAndNameContainingIgnoreCase(ClubStatus status, String name, Pageable pageable);

    List<Club> findByAdminIdOrderByUpdatedAtDesc(Long adminId);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByAdminIdAndStatusIn(Long adminId, Collection<ClubStatus> statuses);

    boolean existsByAdminIdAndStatusInAndIdNot(Long adminId, Collection<ClubStatus> statuses, Long clubId);

    long countByAdminIdAndStatusIn(Long adminId, Collection<ClubStatus> statuses);

    long countByAdminIdAndStatusInAndIdNot(Long adminId, Collection<ClubStatus> statuses, Long clubId);
}

package org.example.pongrankbackend.Community.repository;

import jakarta.persistence.LockModeType;
import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.Community.CommunityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {

    boolean existsByNameIgnoreCaseAndStatus(String name, CommunityStatus status);
    boolean existsByNameIgnoreCaseAndStatusAndIdNot(String name, CommunityStatus status, Long id);

    @Query("""
            SELECT COUNT(c) FROM Community c
            WHERE COALESCE(c.responsibleAdminId, c.creator.id) = :playerId
              AND c.status = :status
            """)
    long countManagedCommunities(@Param("playerId") Long playerId, @Param("status") CommunityStatus status);

    @Query("""
            SELECT c.id FROM Community c
            WHERE c.status = ACTIVE
            """)
    List<Long> findLifecycleCommunityIds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Community c WHERE c.id = :id")
    Optional<Community> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT c FROM Community c
            WHERE c.status = :status
              AND LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))
              AND (:type IS NULL OR c.communityType = :type)
            """)
    Page<Community> searchCommunities(@Param("status") CommunityStatus status,
                                      @Param("name") String name,
                                      @Param("type") CommunityType type,
                                      Pageable pageable);
}

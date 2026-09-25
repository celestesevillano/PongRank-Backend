package org.example.pongrankbackend.CommunityMembership.repository;

import org.example.pongrankbackend.CommunityMembership.CommunityMembership;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.CommunityMembership.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityMembershipRepository extends JpaRepository<CommunityMembership, Long> {
    Optional<CommunityMembership> findByCommunityIdAndPlayerId(Long communityId, Long playerId);

    boolean existsByCommunityIdAndPlayerIdAndRoleAndStatus(Long communityId,
                                                           Long playerId,
                                                           CommunityRole role,
                                                           MembershipStatus status);

    long countByCommunityIdAndRoleAndStatus(Long communityId,
                                            CommunityRole role,
                                            MembershipStatus status);

    long countByCommunityIdAndStatus(Long communityId, MembershipStatus status);

    // rol MEMBER a propósito: la membership del creador es COMMUNITY_ADMIN, así que esto no duplica
    // el conteo de comunidades creadas al calcular el total de pertenencia (creadas + unido como member)
    long countByPlayerIdAndRoleAndStatus(Long playerId, CommunityRole role, MembershipStatus status);

    @Query(value = """
            SELECT m FROM CommunityMembership m
            JOIN FETCH m.player
            WHERE m.community.id = :communityId AND m.status = :status
            """,
            countQuery = """
            SELECT COUNT(m) FROM CommunityMembership m
            WHERE m.community.id = :communityId AND m.status = :status
            """)
    Page<CommunityMembership> findMembersWithPlayer(@Param("communityId") Long communityId,
                                                    @Param("status") MembershipStatus status,
                                                    Pageable pageable);

    @Query(value = """
            SELECT m FROM CommunityMembership m
            JOIN FETCH m.player p
            WHERE m.community.id = :communityId AND m.status = :status
            ORDER BY p.ratingGlicko DESC, p.id ASC
            """,
            countQuery = """
            SELECT COUNT(m) FROM CommunityMembership m
            WHERE m.community.id = :communityId AND m.status = :status
            """)
    Page<CommunityMembership> findRankingWithPlayer(@Param("communityId") Long communityId,
                                                    @Param("status") MembershipStatus status,
                                                    Pageable pageable);

    @Query("""
            SELECT m FROM CommunityMembership m
            JOIN FETCH m.community
            WHERE m.player.id = :playerId AND m.status = :status
            """)
    List<CommunityMembership> findByPlayerIdWithCommunity(@Param("playerId") Long playerId,
                                                          @Param("status") MembershipStatus status);

    @Query("""
            SELECT m.community.id, COUNT(m)
            FROM CommunityMembership m
            WHERE m.community.id IN :communityIds AND m.status = :status
            GROUP BY m.community.id
            """)
    List<Object[]> countActiveMembersByCommunityIds(@Param("communityIds") List<Long> communityIds,
                                                    @Param("status") MembershipStatus status);
}

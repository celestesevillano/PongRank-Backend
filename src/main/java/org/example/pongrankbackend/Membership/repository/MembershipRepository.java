package org.example.pongrankbackend.Membership.repository;

import org.example.pongrankbackend.Membership.Membership;
import org.example.pongrankbackend.Membership.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    // un jugador puede acumular varias filas a lo largo del tiempo (EXPIRED viejas, PENDING nuevas, etc.)
    List<Membership> findByPlayerId(Long playerId);

    Optional<Membership> findByPlayerIdAndStatus(Long playerId, MembershipStatus status);

    // para el job que vence membresías PREMIUM cuyo endDate ya pasó
    List<Membership> findByStatusAndEndDateBefore(MembershipStatus status, LocalDateTime endDate);
}

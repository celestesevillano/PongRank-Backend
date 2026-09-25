package org.example.pongrankbackend.Membership.service;

import org.example.pongrankbackend.Membership.Membership;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.MembershipStatus;
import org.example.pongrankbackend.Membership.repository.MembershipRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class MembershipServiceImpl implements MembershipService {

    private static final int PREMIUM_DURATION_DAYS = 30;

    private final MembershipRepository membershipRepository;
    private final PlayerRepository playerRepository;

    public MembershipServiceImpl(MembershipRepository membershipRepository, PlayerRepository playerRepository) {
        this.membershipRepository = membershipRepository;
        this.playerRepository = playerRepository;
    }

    @Override
    public boolean isPremiumMember(Long playerId) {
        return membershipRepository.findByPlayerIdAndStatus(playerId, MembershipStatus.ACTIVE)
                .filter(membership -> membership.getPlan() == MembershipPlan.PREMIUM)
                .filter(membership -> membership.getEndDate() == null || membership.getEndDate().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    @Override
    @Transactional
    public Membership getOrCreatePendingMembership(Long playerId, MembershipPlan plan) {
        return membershipRepository.findByPlayerIdAndStatus(playerId, MembershipStatus.PENDING)
                .map(pending -> {
                    pending.setPlan(plan);
                    return pending;
                })
                .orElseGet(() -> {
                    Player player = playerRepository.findById(playerId)
                            .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + playerId));
                    Membership membership = Membership.builder()
                            .player(player)
                            .plan(plan)
                            .status(MembershipStatus.PENDING)
                            .build();
                    return membershipRepository.save(membership);
                });
    }

    @Override
    @Transactional
    public void activatePremiumMembership(Membership membership) {
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setStartDate(LocalDateTime.now());
        membership.setEndDate(LocalDateTime.now().plusDays(PREMIUM_DURATION_DAYS));
        membershipRepository.save(membership);
    }
}

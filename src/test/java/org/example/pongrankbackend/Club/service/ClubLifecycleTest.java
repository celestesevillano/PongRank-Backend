package org.example.pongrankbackend.Club.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.Membership.Membership;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.MembershipStatus;
import org.example.pongrankbackend.Membership.repository.MembershipRepository;
import org.example.pongrankbackend.Player.Player;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClubLifecycleTest {
    private final LocalDateTime expiry = LocalDateTime.of(2026, 11, 18, 18, 0);
    private final MembershipRepository plans = mock(MembershipRepository.class);
    private final ClubLifecycle lifecycle = new ClubLifecycle(mock(ClubRepository.class), plans, 72);
    private final Club club = Club.builder().admin(Player.builder().id(1L).build())
            .status(ClubStatus.APPROVED).planExpiresAt(expiry).build();

    @Test
    void pausesAtExpiryAndDeletesAtDeadline() {
        lifecycle.reconcile(club, expiry);
        assertThat(club.getStatus()).isEqualTo(ClubStatus.PAUSED);

        lifecycle.reconcile(club, expiry.plusHours(72));
        assertThat(club.getStatus()).isEqualTo(ClubStatus.DELETED);
    }

    @Test
    void onlyAPlanStartedBeforeTheDeadlineReactivatesAClub() {
        Membership renewed = Membership.builder().plan(MembershipPlan.ENTERPRISE)
                .startDate(expiry.plusHours(71)).endDate(expiry.plusDays(30)).build();
        when(plans.findByPlayerIdAndStatus(1L, MembershipStatus.ACTIVE)).thenReturn(Optional.of(renewed));
        lifecycle.reconcile(club, expiry.plusHours(73));
        assertThat(club.getStatus()).isEqualTo(ClubStatus.APPROVED);
        assertThat(club.getPlanExpiresAt()).isEqualTo(expiry.plusDays(30));

        club.setPlanExpiresAt(expiry);
        renewed.setStartDate(expiry.plusHours(72));
        lifecycle.reconcile(club, expiry.plusHours(73));
        assertThat(club.getStatus()).isEqualTo(ClubStatus.DELETED);
    }
}

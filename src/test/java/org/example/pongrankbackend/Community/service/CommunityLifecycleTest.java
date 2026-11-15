package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.Community.repository.CommunityRepository;
import org.example.pongrankbackend.Membership.Membership;
import org.example.pongrankbackend.Membership.MembershipStatus;
import org.example.pongrankbackend.Membership.repository.MembershipRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityLifecycleTest {
    private static final LocalDateTime EXPIRES = LocalDateTime.of(2026, 11, 18, 18, 0);
    private final MembershipRepository plans = mock(MembershipRepository.class);
    private final CommunityLifecycle lifecycle = new CommunityLifecycle(mock(CommunityRepository.class), plans);
    private final Community community = Community.builder().responsibleAdminId(10L).planExpiresAt(EXPIRES).build();

    @Test
    void archivesAfterOneDayAndDeletesThreeDaysLater() {
        lifecycle.reconcile(community, EXPIRES.plusHours(23));
        assertThat(lifecycle.inGrace(community, EXPIRES.plusHours(23))).isTrue();

        lifecycle.reconcile(community, EXPIRES.plusDays(1));
        assertThat(community.getStatus()).isEqualTo(CommunityStatus.ARCHIVED);
        assertThat(community.getArchivedAt()).isEqualTo(EXPIRES.plusDays(1));

        lifecycle.reconcile(community, EXPIRES.plusDays(4));
        assertThat(community.getStatus()).isEqualTo(CommunityStatus.DELETED);
        assertThat(community.getDeletedAt()).isEqualTo(EXPIRES.plusDays(4));
    }

    @Test
    void timelyRenewalRestoresArchivedCommunity() {
        lifecycle.reconcile(community, EXPIRES.plusDays(1));
        setRenewal(EXPIRES.plusDays(2));

        lifecycle.reconcile(community, EXPIRES.plusDays(3));
        assertThat(community.getStatus()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(community.getArchivedAt()).isNull();
        assertThat(community.getPlanExpiresAt()).isEqualTo(EXPIRES.plusDays(32));
    }

    @Test
    void lateRenewalCannotUndoDeletionEvenIfTheSchedulerWasDelayed() {
        setRenewal(EXPIRES.plusDays(4));

        lifecycle.reconcile(community, EXPIRES.plusDays(4).plusMinutes(1));
        assertThat(community.getStatus()).isEqualTo(CommunityStatus.DELETED);
        assertThat(community.getDeletedAt()).isEqualTo(EXPIRES.plusDays(4));
    }

    private void setRenewal(LocalDateTime startsAt) {
        when(plans.findByPlayerIdAndStatus(10L, MembershipStatus.ACTIVE)).thenReturn(Optional.of(
                Membership.builder().startDate(startsAt).endDate(startsAt.plusDays(30)).build()));
    }
}

package org.example.pongrankbackend.Membership.service;

import org.example.pongrankbackend.Membership.Membership;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.MembershipStatus;
import org.example.pongrankbackend.Membership.repository.MembershipRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipServiceImplTest {

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private MembershipServiceImpl membershipService;

    @ParameterizedTest
    @EnumSource(value = MembershipPlan.class, names = "FREEMIUM", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("getActivePlan: devuelve el plan pagado cuando hay una membresía ACTIVE sin vencer")
    void getActivePlan_ActivePaidNotExpired_ReturnsPlan(MembershipPlan plan) {
        Membership membership = Membership.builder()
                .plan(plan)
                .status(MembershipStatus.ACTIVE)
                .endDate(LocalDateTime.now().plusDays(10))
                .build();
        when(membershipRepository.findByPlayerIdAndStatus(1L, MembershipStatus.ACTIVE)).thenReturn(Optional.of(membership));

        assertThat(membershipService.getActivePlan(1L)).isEqualTo(plan);
        assertThat(membershipService.isPaidMember(1L)).isTrue();
    }

    @Test
    @DisplayName("getActivePlan: FREEMIUM cuando no hay ninguna membresía ACTIVE")
    void getActivePlan_NoActiveMembership_ReturnsFreemium() {
        when(membershipRepository.findByPlayerIdAndStatus(1L, MembershipStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThat(membershipService.getActivePlan(1L)).isEqualTo(MembershipPlan.FREEMIUM);
        assertThat(membershipService.isPaidMember(1L)).isFalse();
    }

    @Test
    @DisplayName("getActivePlan: FREEMIUM cuando endDate ya pasó aunque el status siga en ACTIVE (el scheduler todavía no corrió)")
    void getActivePlan_ActivePaidButEndDatePassed_ReturnsFreemium() {
        Membership membership = Membership.builder()
                .plan(MembershipPlan.PRO)
                .status(MembershipStatus.ACTIVE)
                .endDate(LocalDateTime.now().minusDays(1))
                .build();
        when(membershipRepository.findByPlayerIdAndStatus(1L, MembershipStatus.ACTIVE)).thenReturn(Optional.of(membership));

        assertThat(membershipService.getActivePlan(1L)).isEqualTo(MembershipPlan.FREEMIUM);
    }

    @Test
    @DisplayName("hasCoachAccess: true solo para PRO y ENTERPRISE")
    void hasCoachAccess_OnlyProAndEnterprise() {
        Membership pro = Membership.builder().plan(MembershipPlan.PRO).status(MembershipStatus.ACTIVE).build();
        when(membershipRepository.findByPlayerIdAndStatus(1L, MembershipStatus.ACTIVE)).thenReturn(Optional.of(pro));
        assertThat(membershipService.hasCoachAccess(1L)).isTrue();

        Membership basic = Membership.builder().plan(MembershipPlan.BASIC).status(MembershipStatus.ACTIVE).build();
        when(membershipRepository.findByPlayerIdAndStatus(2L, MembershipStatus.ACTIVE)).thenReturn(Optional.of(basic));
        assertThat(membershipService.hasCoachAccess(2L)).isFalse();
    }

    @Test
    @DisplayName("canCreateClub: true solo para ENTERPRISE")
    void canCreateClub_OnlyEnterprise() {
        Membership enterprise = Membership.builder().plan(MembershipPlan.ENTERPRISE).status(MembershipStatus.ACTIVE).build();
        when(membershipRepository.findByPlayerIdAndStatus(1L, MembershipStatus.ACTIVE)).thenReturn(Optional.of(enterprise));
        assertThat(membershipService.canCreateClub(1L)).isTrue();

        Membership pro = Membership.builder().plan(MembershipPlan.PRO).status(MembershipStatus.ACTIVE).build();
        when(membershipRepository.findByPlayerIdAndStatus(2L, MembershipStatus.ACTIVE)).thenReturn(Optional.of(pro));
        assertThat(membershipService.canCreateClub(2L)).isFalse();
    }

    @Test
    @DisplayName("getOrCreatePendingMembership: reutiliza la membresía PENDING existente y actualiza el plan")
    void getOrCreatePendingMembership_ExistingPending_ReusesAndUpdatesPlan() {
        Membership pending = Membership.builder()
                .id(5L)
                .plan(MembershipPlan.FREEMIUM)
                .status(MembershipStatus.PENDING)
                .build();
        when(membershipRepository.findByPlayerIdAndStatus(1L, MembershipStatus.PENDING)).thenReturn(Optional.of(pending));

        Membership result = membershipService.getOrCreatePendingMembership(1L, MembershipPlan.PRO);

        assertThat(result).isSameAs(pending);
        assertThat(result.getPlan()).isEqualTo(MembershipPlan.PRO);
        verify(membershipRepository, never()).save(any());
        verify(playerRepository, never()).findById(any());
    }

    @Test
    @DisplayName("getOrCreatePendingMembership: crea una nueva membresía PENDING cuando no hay ninguna")
    void getOrCreatePendingMembership_NoPending_CreatesNew() {
        Player player = Player.builder().id(1L).build();
        when(membershipRepository.findByPlayerIdAndStatus(1L, MembershipStatus.PENDING)).thenReturn(Optional.empty());
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Membership result = membershipService.getOrCreatePendingMembership(1L, MembershipPlan.PRO);

        assertThat(result.getPlayer()).isSameAs(player);
        assertThat(result.getPlan()).isEqualTo(MembershipPlan.PRO);
        assertThat(result.getStatus()).isEqualTo(MembershipStatus.PENDING);
    }

    @Test
    @DisplayName("getOrCreatePendingMembership: lanza ResourceNotFoundException cuando el jugador no existe")
    void getOrCreatePendingMembership_PlayerNotFound_ThrowsException() {
        when(membershipRepository.findByPlayerIdAndStatus(99L, MembershipStatus.PENDING)).thenReturn(Optional.empty());
        when(playerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.getOrCreatePendingMembership(99L, MembershipPlan.PRO))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Jugador no encontrado");
    }

    @Test
    @DisplayName("activatePaidMembership: pone ACTIVE, fija startDate y endDate a 30 días")
    void activatePaidMembership_SetsActiveWithThirtyDayWindow() {
        Membership membership = Membership.builder().plan(MembershipPlan.PRO).status(MembershipStatus.PENDING).build();

        membershipService.activatePaidMembership(membership);

        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.getStartDate()).isNotNull();
        assertThat(membership.getEndDate()).isAfter(membership.getStartDate().plusDays(29));
        assertThat(membership.getEndDate()).isBefore(membership.getStartDate().plusDays(31));
        verify(membershipRepository).save(membership);
    }

    @Test
    @DisplayName("expireOverdueMemberships: pasa a EXPIRED todas las ACTIVE con endDate vencido")
    void expireOverdueMemberships_MarksOverdueAsExpired() {
        Membership overdue1 = Membership.builder().id(1L).status(MembershipStatus.ACTIVE).endDate(LocalDateTime.now().minusDays(1)).build();
        Membership overdue2 = Membership.builder().id(2L).status(MembershipStatus.ACTIVE).endDate(LocalDateTime.now().minusHours(1)).build();
        when(membershipRepository.findByStatusAndEndDateBefore(eq(MembershipStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(overdue1, overdue2));

        membershipService.expireOverdueMemberships();

        assertThat(overdue1.getStatus()).isEqualTo(MembershipStatus.EXPIRED);
        assertThat(overdue2.getStatus()).isEqualTo(MembershipStatus.EXPIRED);

        ArgumentCaptor<List<Membership>> captor = ArgumentCaptor.forClass(List.class);
        verify(membershipRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(overdue1, overdue2);
    }

    @Test
    @DisplayName("expireOverdueMemberships: no hace nada cuando no hay membresías vencidas")
    void expireOverdueMemberships_NoneOverdue_SavesEmptyList() {
        when(membershipRepository.findByStatusAndEndDateBefore(eq(MembershipStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of());

        membershipService.expireOverdueMemberships();

        verify(membershipRepository).saveAll(List.of());
    }
}

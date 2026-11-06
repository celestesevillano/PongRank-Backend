package org.example.pongrankbackend.Membership.service;

import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.dto.MyPlanResponseDTO;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyPlanServiceImplTest {

    private static final Long PLAYER_ID = 3L;

    @Mock
    private MembershipService membershipService;

    @InjectMocks
    private MyPlanServiceImpl service;

    @BeforeEach
    void setUp() {
        int[][] values = {{1, 5, 8, 8}, {2, 7, 10, 10}, {0, 2, 5, 1}};
        String[] plans = {"Freemium", "Basic", "Pro", "Enterprise"};
        String[] groups = {"created", "communities", "clubs"};
        for (int g = 0; g < groups.length; g++) {
            for (int p = 0; p < plans.length; p++) {
                ReflectionTestUtils.setField(service, groups[g] + plans[p], values[g][p]);
            }
        }
        CustomUserDetails userDetails = new CustomUserDetails(
                Player.builder().id(PLAYER_ID).name("Jugador").role(Role.ROLE_USER).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getMyPlan: un jugador FREEMIUM no tiene entrenador ni clubes")
    void getMyPlan_Freemium() {
        when(membershipService.getActivePlan(PLAYER_ID)).thenReturn(MembershipPlan.FREEMIUM);
        when(membershipService.getActivePlanEndDate(PLAYER_ID)).thenReturn(Optional.empty());

        MyPlanResponseDTO response = service.getMyPlan();

        assertThat(response.getPlan()).isEqualTo(MembershipPlan.FREEMIUM);
        assertThat(response.getEndDate()).isNull();
        assertThat(response.isCoachAccess()).isFalse();
        assertThat(response.isCanCreateClub()).isFalse();
        assertThat(response.getMaxCommunitiesCreated()).isEqualTo(1);
        assertThat(response.getMaxCommunitiesTotal()).isEqualTo(2);
        assertThat(response.getMaxClubsTotal()).isZero();
    }

    @Test
    @DisplayName("getMyPlan: un jugador PRO tiene entrenador y sus límites, pero no crea clubes")
    void getMyPlan_Pro() {
        LocalDateTime end = LocalDateTime.now().plusDays(10);
        when(membershipService.getActivePlan(PLAYER_ID)).thenReturn(MembershipPlan.PRO);
        when(membershipService.getActivePlanEndDate(PLAYER_ID)).thenReturn(Optional.of(end));
        when(membershipService.hasCoachAccess(PLAYER_ID)).thenReturn(true);
        when(membershipService.canCreateClub(PLAYER_ID)).thenReturn(false);

        MyPlanResponseDTO response = service.getMyPlan();

        assertThat(response.getPlan()).isEqualTo(MembershipPlan.PRO);
        assertThat(response.getEndDate()).isEqualTo(end);
        assertThat(response.isCoachAccess()).isTrue();
        assertThat(response.isCanCreateClub()).isFalse();
        assertThat(response.getMaxCommunitiesCreated()).isEqualTo(8);
        assertThat(response.getMaxCommunitiesTotal()).isEqualTo(10);
        assertThat(response.getMaxClubsTotal()).isEqualTo(5);
    }

    @Test
    @DisplayName("getMyPlan: un jugador ENTERPRISE puede crear clubes")
    void getMyPlan_Enterprise() {
        when(membershipService.getActivePlan(PLAYER_ID)).thenReturn(MembershipPlan.ENTERPRISE);
        when(membershipService.getActivePlanEndDate(PLAYER_ID)).thenReturn(Optional.empty());
        when(membershipService.hasCoachAccess(PLAYER_ID)).thenReturn(true);
        when(membershipService.canCreateClub(PLAYER_ID)).thenReturn(true);

        MyPlanResponseDTO response = service.getMyPlan();

        assertThat(response.isCanCreateClub()).isTrue();
        assertThat(response.getMaxClubsTotal()).isEqualTo(1);
    }
}

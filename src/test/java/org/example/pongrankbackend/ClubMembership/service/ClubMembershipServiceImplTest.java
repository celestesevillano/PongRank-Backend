package org.example.pongrankbackend.ClubMembership.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.ClubMembership.ClubMembership;
import org.example.pongrankbackend.ClubMembership.ClubMembershipRole;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.ClubMembership.ClubMembershipStatus;
import org.example.pongrankbackend.ClubMembership.dto.ClubMembershipRequestDTO;
import org.example.pongrankbackend.ClubMembership.repository.ClubMembershipRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.modelmapper.ModelMapper;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClubMembershipServiceImplTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long PLAYER_ID = 3L;
    private static final Long OTHER_PLAYER_ID = 4L;
    private static final Long CLUB_ID = 10L;
    private static final Long MEMBERSHIP_ID = 50L;

    @Mock
    private ClubMembershipRepository clubMembershipRepository;

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private org.example.pongrankbackend.Membership.service.MembershipService membershipService;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private ClubMembershipServiceImpl clubMembershipService;

    @BeforeEach
    void setUp() {
        lenient().when(membershipService.getActivePlan(anyLong())).thenReturn(MembershipPlan.PRO);
        org.springframework.test.util.ReflectionTestUtils.setField(clubMembershipService, "maxTotalFreemium", 0);
        org.springframework.test.util.ReflectionTestUtils.setField(clubMembershipService, "maxTotalBasic", 2);
        org.springframework.test.util.ReflectionTestUtils.setField(clubMembershipService, "maxTotalPro", 5);
        org.springframework.test.util.ReflectionTestUtils.setField(clubMembershipService, "maxTotalEnterprise", 1);
    }

    private Player player(Long id) {
        return Player.builder().id(id).name("Jugador " + id).build();
    }

    private Club club(ClubStatus status) {
        return Club.builder()
                .id(CLUB_ID)
                .name("Club Lima")
                .admin(player(ADMIN_ID))
                .status(status)
                .build();
    }

    private ClubMembership membership(Player player, ClubStatus clubStatus,
                                      ClubMembershipStatus status, ClubMembershipRole role) {
        return ClubMembership.builder()
                .id(MEMBERSHIP_ID)
                .player(player)
                .club(club(clubStatus))
                .status(status)
                .role(role)
                .build();
    }

    private ClubMembershipRequestDTO requestFor(Long clubId) {
        return ClubMembershipRequestDTO.builder().clubId(clubId).build();
    }

    // Simula el JWT validado: pone al jugador dado como usuario autenticado actual
    private void actingAs(Long playerId) {
        CustomUserDetails userDetails = new CustomUserDetails(
                Player.builder().id(playerId).name("Jugador " + playerId).role(org.example.pongrankbackend.Player.Role.ROLE_USER).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("requestMembership: solo se puede solicitar ingreso a clubes APPROVED")
    void requestMembership_ClubNotApproved_ThrowsException() {
        actingAs(PLAYER_ID);
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player(PLAYER_ID)));
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.PENDING)));

        assertThatThrownBy(() -> clubMembershipService.requestMembership(requestFor(CLUB_ID)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no está aprobado");

        verify(clubMembershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestMembership: no permite una solicitud duplicada al mismo club")
    void requestMembership_DuplicateInSameClub_ThrowsException() {
        actingAs(PLAYER_ID);
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player(PLAYER_ID)));
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.APPROVED)));
        when(clubMembershipRepository.existsByPlayerIdAndClubIdAndStatusIn(
                PLAYER_ID, CLUB_ID, ClubMembershipStatus.ACTIVE_STATUSES)).thenReturn(true);

        assertThatThrownBy(() -> clubMembershipService.requestMembership(requestFor(CLUB_ID)))
                .isInstanceOf(ConflictException.class);

        verify(clubMembershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestMembership: no permite unirse a más clubes de los que su plan permite")
    void requestMembership_ExceedsPlanLimit_ThrowsException() {
        actingAs(PLAYER_ID);
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player(PLAYER_ID)));
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.APPROVED)));
        when(membershipService.getActivePlan(PLAYER_ID)).thenReturn(MembershipPlan.BASIC);
        when(clubMembershipRepository.countByPlayerIdAndStatusIn(PLAYER_ID, ClubMembershipStatus.ACTIVE_STATUSES))
                .thenReturn(2L);

        assertThatThrownBy(() -> clubMembershipService.requestMembership(requestFor(CLUB_ID)))
                .isInstanceOf(org.example.pongrankbackend.common.exception.PlanRestrictionException.class);

        verify(clubMembershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestMembership: el administrador de un club en curso no puede unirse a otro")
    void requestMembership_AdminOfActiveClub_ThrowsException() {
        actingAs(PLAYER_ID);
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player(PLAYER_ID)));
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.APPROVED)));
        when(clubRepository.existsByAdminIdAndStatusIn(PLAYER_ID, ClubStatus.ACTIVE_STATUSES)).thenReturn(true);

        assertThatThrownBy(() -> clubMembershipService.requestMembership(requestFor(CLUB_ID)))
                .isInstanceOf(ConflictException.class);

        verify(clubMembershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestMembership: un jugador FREEMIUM no puede unirse a un club")
    void requestMembership_FreemiumPlan_ThrowsException() {
        actingAs(PLAYER_ID);
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player(PLAYER_ID)));
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.APPROVED)));
        when(membershipService.getActivePlan(PLAYER_ID)).thenReturn(MembershipPlan.FREEMIUM);

        assertThatThrownBy(() -> clubMembershipService.requestMembership(requestFor(CLUB_ID)))
                .isInstanceOf(org.example.pongrankbackend.common.exception.PlanRestrictionException.class);

        verify(clubMembershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestMembership: la nueva solicitud comienza como PENDING con rol MEMBER")
    void requestMembership_Success_CreatesPendingRequest() {
        actingAs(PLAYER_ID);
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player(PLAYER_ID)));
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.APPROVED)));
        when(clubMembershipRepository.save(any(ClubMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        clubMembershipService.requestMembership(requestFor(CLUB_ID));

        ArgumentCaptor<ClubMembership> captor = ArgumentCaptor.forClass(ClubMembership.class);
        verify(clubMembershipRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ClubMembershipStatus.PENDING);
        assertThat(captor.getValue().getRole()).isEqualTo(ClubMembershipRole.MEMBER);
        assertThat(captor.getValue().getJoinedAt()).isNull();
    }

    @Test
    @DisplayName("approveRequest: solo el administrador del club puede aprobar")
    void approveRequest_NotClubAdmin_ThrowsException() {
        actingAs(OTHER_PLAYER_ID);
        ClubMembership membership = membership(player(PLAYER_ID), ClubStatus.APPROVED,
                ClubMembershipStatus.PENDING, ClubMembershipRole.MEMBER);
        when(clubMembershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> clubMembershipService.approveRequest(MEMBERSHIP_ID))
                .isInstanceOf(UnauthorizedActionException.class);

        assertThat(membership.getStatus()).isEqualTo(ClubMembershipStatus.PENDING);
    }

    @Test
    @DisplayName("approveRequest: aprueba la solicitud y registra la fecha de ingreso")
    void approveRequest_Success_SetsJoinedAt() {
        actingAs(ADMIN_ID);
        ClubMembership membership = membership(player(PLAYER_ID), ClubStatus.APPROVED,
                ClubMembershipStatus.PENDING, ClubMembershipRole.MEMBER);
        when(clubMembershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));

        clubMembershipService.approveRequest(MEMBERSHIP_ID);

        assertThat(membership.getStatus()).isEqualTo(ClubMembershipStatus.APPROVED);
        assertThat(membership.getJoinedAt()).isNotNull();
    }

    @Test
    @DisplayName("rejectRequest: la solicitud queda REJECTED y se conserva")
    void rejectRequest_Success_KeepsRecord() {
        actingAs(ADMIN_ID);
        ClubMembership membership = membership(player(PLAYER_ID), ClubStatus.APPROVED,
                ClubMembershipStatus.PENDING, ClubMembershipRole.MEMBER);
        when(clubMembershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));

        clubMembershipService.rejectRequest(MEMBERSHIP_ID);

        assertThat(membership.getStatus()).isEqualTo(ClubMembershipStatus.REJECTED);
        verify(clubMembershipRepository, never()).delete(any());
    }

    @Test
    @DisplayName("cancelRequest: solo el propio jugador puede cancelar su solicitud")
    void cancelRequest_OtherPlayer_ThrowsException() {
        actingAs(OTHER_PLAYER_ID);
        ClubMembership membership = membership(player(PLAYER_ID), ClubStatus.APPROVED,
                ClubMembershipStatus.PENDING, ClubMembershipRole.MEMBER);
        when(clubMembershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> clubMembershipService.cancelRequest(MEMBERSHIP_ID))
                .isInstanceOf(UnauthorizedActionException.class);
    }

    @Test
    @DisplayName("cancelRequest: la solicitud pendiente queda CANCELLED")
    void cancelRequest_Success() {
        actingAs(PLAYER_ID);
        ClubMembership membership = membership(player(PLAYER_ID), ClubStatus.APPROVED,
                ClubMembershipStatus.PENDING, ClubMembershipRole.MEMBER);
        when(clubMembershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));

        clubMembershipService.cancelRequest(MEMBERSHIP_ID);

        assertThat(membership.getStatus()).isEqualTo(ClubMembershipStatus.CANCELLED);
    }

    @Test
    @DisplayName("leaveClub: el administrador no puede abandonar su club sin transferir la administración")
    void leaveClub_ClubAdmin_ThrowsException() {
        actingAs(ADMIN_ID);
        ClubMembership membership = membership(player(ADMIN_ID), ClubStatus.APPROVED,
                ClubMembershipStatus.APPROVED, ClubMembershipRole.CLUB_ADMIN);
        when(clubMembershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> clubMembershipService.leaveClub(MEMBERSHIP_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("transferir");

        assertThat(membership.getStatus()).isEqualTo(ClubMembershipStatus.APPROVED);
    }

    @Test
    @DisplayName("leaveClub: el miembro queda LEFT con fecha de salida y el registro se conserva")
    void leaveClub_Member_Success() {
        actingAs(PLAYER_ID);
        ClubMembership membership = membership(player(PLAYER_ID), ClubStatus.APPROVED,
                ClubMembershipStatus.APPROVED, ClubMembershipRole.MEMBER);
        when(clubMembershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));

        clubMembershipService.leaveClub(MEMBERSHIP_ID);

        assertThat(membership.getStatus()).isEqualTo(ClubMembershipStatus.LEFT);
        assertThat(membership.getLeftAt()).isNotNull();
        verify(clubMembershipRepository, never()).delete(any());
    }

    @Test
    @DisplayName("addAdminAsMember: no duplica la membresía si el administrador ya es miembro")
    void addAdminAsMember_AlreadyMember_DoesNotDuplicate() {
        Club club = club(ClubStatus.APPROVED);
        ClubMembership existing = membership(club.getAdmin(), ClubStatus.APPROVED,
                ClubMembershipStatus.APPROVED, ClubMembershipRole.CLUB_ADMIN);
        when(clubMembershipRepository.findByPlayerIdAndClubIdAndStatus(ADMIN_ID, CLUB_ID, ClubMembershipStatus.APPROVED))
                .thenReturn(Optional.of(existing));

        clubMembershipService.addAdminAsMember(club);

        verify(clubMembershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("addAdminAsMember: crea la membresía APPROVED con rol CLUB_ADMIN y fecha de ingreso")
    void addAdminAsMember_Success_CreatesAdminMembership() {
        Club club = club(ClubStatus.APPROVED);
        when(clubMembershipRepository.findByPlayerIdAndClubIdAndStatus(ADMIN_ID, CLUB_ID, ClubMembershipStatus.APPROVED))
                .thenReturn(Optional.empty());

        clubMembershipService.addAdminAsMember(club);

        ArgumentCaptor<ClubMembership> captor = ArgumentCaptor.forClass(ClubMembership.class);
        verify(clubMembershipRepository).save(captor.capture());
        ClubMembership created = captor.getValue();
        assertThat(created.getStatus()).isEqualTo(ClubMembershipStatus.APPROVED);
        assertThat(created.getRole()).isEqualTo(ClubMembershipRole.CLUB_ADMIN);
        assertThat(created.getPlayer()).isEqualTo(club.getAdmin());
        assertThat(created.getJoinedAt()).isNotNull();
    }

    @Test
    @DisplayName("addAdminAsMember: falla si el administrador ya pertenece a otro club")
    void addAdminAsMember_ActiveElsewhere_ThrowsException() {
        Club club = club(ClubStatus.APPROVED);
        when(clubMembershipRepository.findByPlayerIdAndClubIdAndStatus(ADMIN_ID, CLUB_ID, ClubMembershipStatus.APPROVED))
                .thenReturn(Optional.empty());
        when(membershipService.getActivePlan(ADMIN_ID)).thenReturn(MembershipPlan.ENTERPRISE);
        when(clubMembershipRepository.countByPlayerIdAndStatusIn(ADMIN_ID, ClubMembershipStatus.ACTIVE_STATUSES))
                .thenReturn(1L);

        assertThatThrownBy(() -> clubMembershipService.addAdminAsMember(club))
                .isInstanceOf(org.example.pongrankbackend.common.exception.PlanRestrictionException.class);

        verify(clubMembershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("transferAdminRole: el nuevo administrador debe ser miembro activo del club")
    void transferAdminRole_NewAdminNotMember_ThrowsException() {
        Club club = club(ClubStatus.APPROVED);
        ClubMembership adminMembership = membership(club.getAdmin(), ClubStatus.APPROVED,
                ClubMembershipStatus.APPROVED, ClubMembershipRole.CLUB_ADMIN);
        when(clubMembershipRepository.findByPlayerIdAndClubIdAndStatus(ADMIN_ID, CLUB_ID, ClubMembershipStatus.APPROVED))
                .thenReturn(Optional.of(adminMembership));
        when(clubMembershipRepository.findByPlayerIdAndClubIdAndStatus(PLAYER_ID, CLUB_ID, ClubMembershipStatus.APPROVED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> clubMembershipService.transferAdminRole(club, PLAYER_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("miembro activo");

        assertThat(adminMembership.getRole()).isEqualTo(ClubMembershipRole.CLUB_ADMIN);
    }

    @Test
    @DisplayName("transferAdminRole: intercambia los roles de ambas membresías")
    void transferAdminRole_Success_SwapsRoles() {
        Club club = club(ClubStatus.APPROVED);
        ClubMembership adminMembership = membership(club.getAdmin(), ClubStatus.APPROVED,
                ClubMembershipStatus.APPROVED, ClubMembershipRole.CLUB_ADMIN);
        Player newAdmin = player(PLAYER_ID);
        ClubMembership memberMembership = membership(newAdmin, ClubStatus.APPROVED,
                ClubMembershipStatus.APPROVED, ClubMembershipRole.MEMBER);
        when(clubMembershipRepository.findByPlayerIdAndClubIdAndStatus(ADMIN_ID, CLUB_ID, ClubMembershipStatus.APPROVED))
                .thenReturn(Optional.of(adminMembership));
        when(clubMembershipRepository.findByPlayerIdAndClubIdAndStatus(PLAYER_ID, CLUB_ID, ClubMembershipStatus.APPROVED))
                .thenReturn(Optional.of(memberMembership));

        Player result = clubMembershipService.transferAdminRole(club, PLAYER_ID);

        assertThat(result).isEqualTo(newAdmin);
        assertThat(adminMembership.getRole()).isEqualTo(ClubMembershipRole.MEMBER);
        assertThat(memberMembership.getRole()).isEqualTo(ClubMembershipRole.CLUB_ADMIN);
    }

    @Test
    @DisplayName("cancelRequest: no se puede cancelar una solicitud ya aprobada")
    void cancelRequest_AlreadyApproved_ThrowsException() {
        actingAs(PLAYER_ID);
        ClubMembership membership = membership(player(PLAYER_ID), ClubStatus.APPROVED,
                ClubMembershipStatus.APPROVED, ClubMembershipRole.MEMBER);
        when(clubMembershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> clubMembershipService.cancelRequest(MEMBERSHIP_ID))
                .isInstanceOf(ConflictException.class);
        assertThat(membership.getStatus()).isEqualTo(ClubMembershipStatus.APPROVED);
    }

    @Test
    @DisplayName("approveRequest: no se puede aprobar dos veces la misma solicitud")
    void approveRequest_AlreadyApproved_ThrowsException() {
        actingAs(ADMIN_ID);
        ClubMembership membership = membership(player(PLAYER_ID), ClubStatus.APPROVED,
                ClubMembershipStatus.APPROVED, ClubMembershipRole.MEMBER);
        when(clubMembershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> clubMembershipService.approveRequest(MEMBERSHIP_ID))
                .isInstanceOf(ConflictException.class);
        verify(clubMembershipRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("rejectRequest: no se puede rechazar una solicitud ya resuelta (CANCELLED)")
    void rejectRequest_AlreadyResolved_ThrowsException() {
        actingAs(ADMIN_ID);
        ClubMembership membership = membership(player(PLAYER_ID), ClubStatus.APPROVED,
                ClubMembershipStatus.CANCELLED, ClubMembershipRole.MEMBER);
        when(clubMembershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> clubMembershipService.rejectRequest(MEMBERSHIP_ID))
                .isInstanceOf(ConflictException.class);
        assertThat(membership.getStatus()).isEqualTo(ClubMembershipStatus.CANCELLED);
    }

    @Test
    @DisplayName("approveRequest: no aprueba si el club dejó de estar APPROVED (re-verificación)")
    void approveRequest_ClubUnderReverification_ThrowsException() {
        actingAs(ADMIN_ID);
        ClubMembership membership = membership(player(PLAYER_ID), ClubStatus.PENDING,
                ClubMembershipStatus.PENDING, ClubMembershipRole.MEMBER);
        when(clubMembershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> clubMembershipService.approveRequest(MEMBERSHIP_ID))
                .isInstanceOf(ConflictException.class);
        assertThat(membership.getStatus()).isEqualTo(ClubMembershipStatus.PENDING);
    }

    @Test
    @DisplayName("isActiveMember: solo cuenta la membresía APPROVED")
    void isActiveMember_UsesApprovedMembership() {
        when(clubMembershipRepository.findByPlayerIdAndClubIdAndStatus(PLAYER_ID, CLUB_ID, ClubMembershipStatus.APPROVED))
                .thenReturn(Optional.empty());

        assertThat(clubMembershipService.isActiveMember(CLUB_ID, PLAYER_ID)).isFalse();
    }

    @Test
    @DisplayName("getActiveMembers: pagina en la base de datos solo membresías APPROVED del club")
    void getActiveMembers_PaginatesApprovedMemberships() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.APPROVED)));
        when(clubMembershipRepository.findByClubIdAndStatus(eq(CLUB_ID), eq(ClubMembershipStatus.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        PageResponseDTO<?> result = clubMembershipService.getActiveMembers(CLUB_ID, 0, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clubMembershipRepository).findByClubIdAndStatus(eq(CLUB_ID), eq(ClubMembershipStatus.APPROVED), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("joinedAt").ascending());
        assertThat(result.getContent()).isEmpty();
        assertThat(result.isFirst()).isTrue();
    }

    @Test
    @DisplayName("getPendingRequests: solo el administrador del club puede listar solicitudes")
    void getPendingRequests_NotClubAdmin_ThrowsBeforeQuerying() {
        actingAs(OTHER_PLAYER_ID);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.APPROVED)));

        assertThatThrownBy(() -> clubMembershipService.getPendingRequests(CLUB_ID, 0, 10))
                .isInstanceOf(UnauthorizedActionException.class);

        verify(clubMembershipRepository, never()).findByClubIdAndStatus(any(), any(), any());
    }

    @Test
    @DisplayName("getPlayerMembershipHistory: historial paginado del más reciente al más antiguo")
    void getPlayerMembershipHistory_PaginatesNewestFirst() {
        when(playerRepository.existsById(PLAYER_ID)).thenReturn(true);
        when(clubMembershipRepository.findByPlayerId(eq(PLAYER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        clubMembershipService.getPlayerMembershipHistory(PLAYER_ID, 0, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clubMembershipRepository).findByPlayerId(eq(PLAYER_ID), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("createdAt").descending());
    }
}

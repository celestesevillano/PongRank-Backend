package org.example.pongrankbackend.Club.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.ClubReview;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Club.dto.ClubAdminTransferRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubAffiliationDocumentRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubRegisterRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubRejectRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubResubmitRequestDTO;
import org.example.pongrankbackend.Club.dto.ClubUpdateRequestDTO;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.Club.repository.ClubReviewRepository;
import org.example.pongrankbackend.ClubMembership.service.ClubMembershipService;
import org.example.pongrankbackend.Membership.service.MembershipService;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.PlanRestrictionException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.modelmapper.ModelMapper;
import org.example.pongrankbackend.common.pagination.PageRequestFactory;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.example.pongrankbackend.Club.dto.ClubResponseDTO;
import java.util.List;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClubServiceImplTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long OTHER_PLAYER_ID = 2L;
    private static final Long SYSTEM_ADMIN_ID = 99L;
    private static final Long CLUB_ID = 10L;

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private ClubReviewRepository clubReviewRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private ClubMembershipService clubMembershipService;

    @Mock
    private MembershipService membershipService;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private ClubServiceImpl clubService;

    private Player player(Long id, Role role) {
        return Player.builder().id(id).name("Jugador " + id).role(role).build();
    }

    private Club club(ClubStatus status) {
        return Club.builder()
                .id(CLUB_ID)
                .name("Club Lima")
                .address("Av. Lima 123")
                .affiliationDocumentUrl("https://docs.test/afiliacion.pdf")
                .admin(player(ADMIN_ID, Role.ROLE_USER))
                .status(status)
                .build();
    }

    // Simula el JWT validado: pone al jugador dado como usuario autenticado actual
    private void actingAs(Long playerId, Role role) {
        CustomUserDetails userDetails = new CustomUserDetails(player(playerId, role));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("registerClub: crea el club en PENDING con el solicitante como administrador")
    void registerClub_Success_CreatesPendingClub() {
        actingAs(ADMIN_ID, Role.ROLE_USER);
        Player requester = player(ADMIN_ID, Role.ROLE_USER);
        ClubRegisterRequestDTO dto = ClubRegisterRequestDTO.builder()
                .name("  Club Lima ")
                .address("Av. Lima 123")
                .affiliationDocumentUrl("https://docs.test/afiliacion.pdf")
                .build();

        when(playerRepository.findById(ADMIN_ID)).thenReturn(Optional.of(requester));
        when(membershipService.canCreateClub(ADMIN_ID)).thenReturn(true);
        when(clubRepository.save(any(Club.class))).thenAnswer(invocation -> invocation.getArgument(0));

        clubService.registerClub(dto);

        ArgumentCaptor<Club> captor = ArgumentCaptor.forClass(Club.class);
        verify(clubRepository).save(captor.capture());
        Club saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ClubStatus.PENDING);
        assertThat(saved.getAdmin()).isEqualTo(requester);
        assertThat(saved.getName()).isEqualTo("Club Lima");
        assertThat(requester.getRole()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    @DisplayName("registerClub: rechaza un nombre duplicado ignorando mayúsculas")
    void registerClub_DuplicateName_ThrowsException() {
        actingAs(ADMIN_ID, Role.ROLE_USER);
        ClubRegisterRequestDTO dto = ClubRegisterRequestDTO.builder()
                .name("Club Lima").address("Av. Lima 123").affiliationDocumentUrl("https://docs.test/a.pdf").build();

        when(playerRepository.findById(ADMIN_ID)).thenReturn(Optional.of(player(ADMIN_ID, Role.ROLE_USER)));
        when(membershipService.canCreateClub(ADMIN_ID)).thenReturn(true);
        when(clubRepository.existsByNameIgnoreCase("Club Lima")).thenReturn(true);

        assertThatThrownBy(() -> clubService.registerClub(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ya existe un club");

        verify(clubRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerClub: rechaza al solicitante que no tiene plan ENTERPRISE")
    void registerClub_NotEnterprisePlan_ThrowsException() {
        actingAs(ADMIN_ID, Role.ROLE_USER);
        ClubRegisterRequestDTO dto = ClubRegisterRequestDTO.builder()
                .name("Club Lima").address("Av. Lima 123").affiliationDocumentUrl("https://docs.test/a.pdf").build();

        when(playerRepository.findById(ADMIN_ID)).thenReturn(Optional.of(player(ADMIN_ID, Role.ROLE_USER)));
        when(membershipService.canCreateClub(ADMIN_ID)).thenReturn(false);

        assertThatThrownBy(() -> clubService.registerClub(dto))
                .isInstanceOf(PlanRestrictionException.class)
                .hasMessageContaining("ENTERPRISE");

        verify(clubRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerClub: un jugador que ya pertenece a un club no puede registrar otro")
    void registerClub_RequesterWithActiveMembership_ThrowsException() {
        actingAs(ADMIN_ID, Role.ROLE_USER);
        ClubRegisterRequestDTO dto = ClubRegisterRequestDTO.builder()
                .name("Club Nuevo").address("Av. Lima 123").affiliationDocumentUrl("https://docs.test/a.pdf").build();

        when(playerRepository.findById(ADMIN_ID)).thenReturn(Optional.of(player(ADMIN_ID, Role.ROLE_USER)));
        when(clubMembershipService.hasActiveMembershipOutsideClub(ADMIN_ID, null)).thenReturn(true);

        assertThatThrownBy(() -> clubService.registerClub(dto))
                .isInstanceOf(ConflictException.class);

        verify(clubRepository, never()).save(any());
    }

    @Test
    @DisplayName("approveClub: solo el administrador general puede aprobar")
    void approveClub_NotSystemAdmin_ThrowsException() {
        actingAs(OTHER_PLAYER_ID, Role.ROLE_USER);
        when(playerRepository.findById(OTHER_PLAYER_ID)).thenReturn(Optional.of(player(OTHER_PLAYER_ID, Role.ROLE_USER)));

        assertThatThrownBy(() -> clubService.approveClub(CLUB_ID))
                .isInstanceOf(UnauthorizedActionException.class)
                .hasMessageContaining("administrador general");

        verify(clubRepository, never()).saveAndFlush(any());
        verify(clubMembershipService, never()).addAdminAsMember(any());
    }

    @Test
    @DisplayName("approveClub: aprueba, guarda el historial y crea la membresía del administrador")
    void approveClub_Success_RecordsReviewAndAddsAdminMembership() {
        actingAs(SYSTEM_ADMIN_ID, Role.ROLE_SYSTEM_ADMIN);
        Player systemAdmin = player(SYSTEM_ADMIN_ID, Role.ROLE_SYSTEM_ADMIN);
        Club club = club(ClubStatus.PENDING);

        when(playerRepository.findById(SYSTEM_ADMIN_ID)).thenReturn(Optional.of(systemAdmin));
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club));

        clubService.approveClub(CLUB_ID);

        assertThat(club.getStatus()).isEqualTo(ClubStatus.APPROVED);
        ArgumentCaptor<ClubReview> captor = ArgumentCaptor.forClass(ClubReview.class);
        verify(clubReviewRepository).save(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo(ClubStatus.APPROVED);
        assertThat(captor.getValue().getReviewer()).isEqualTo(systemAdmin);
        verify(clubMembershipService).addAdminAsMember(club);
    }

    @Test
    @DisplayName("approveClub: si falla la membresía del administrador, la excepción interrumpe la aprobación (rollback)")
    void approveClub_MembershipFails_PropagatesException() {
        actingAs(SYSTEM_ADMIN_ID, Role.ROLE_SYSTEM_ADMIN);
        Club club = club(ClubStatus.PENDING);

        when(playerRepository.findById(SYSTEM_ADMIN_ID)).thenReturn(Optional.of(player(SYSTEM_ADMIN_ID, Role.ROLE_SYSTEM_ADMIN)));
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club));
        doThrow(new ConflictException("fallo al crear membresía"))
                .when(clubMembershipService).addAdminAsMember(club);

        assertThatThrownBy(() -> clubService.approveClub(CLUB_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("fallo al crear membresía");
    }

    @Test
    @DisplayName("approveClub: no aprueba si el administrador ya pertenece a otro club")
    void approveClub_AdminBelongsToAnotherClub_ThrowsException() {
        actingAs(SYSTEM_ADMIN_ID, Role.ROLE_SYSTEM_ADMIN);
        when(playerRepository.findById(SYSTEM_ADMIN_ID)).thenReturn(Optional.of(player(SYSTEM_ADMIN_ID, Role.ROLE_SYSTEM_ADMIN)));
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.PENDING)));
        when(clubMembershipService.hasActiveMembershipOutsideClub(ADMIN_ID, CLUB_ID)).thenReturn(true);

        assertThatThrownBy(() -> clubService.approveClub(CLUB_ID))
                .isInstanceOf(ConflictException.class);

        verify(clubRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("rejectClub: guarda el motivo y registra la revisión en el historial")
    void rejectClub_Success_SavesReasonAndHistory() {
        actingAs(SYSTEM_ADMIN_ID, Role.ROLE_SYSTEM_ADMIN);
        Club club = club(ClubStatus.PENDING);
        ClubRejectRequestDTO dto = ClubRejectRequestDTO.builder().rejectionReason("Documento ilegible").build();

        when(playerRepository.findById(SYSTEM_ADMIN_ID)).thenReturn(Optional.of(player(SYSTEM_ADMIN_ID, Role.ROLE_SYSTEM_ADMIN)));
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club));

        clubService.rejectClub(CLUB_ID, dto);

        assertThat(club.getStatus()).isEqualTo(ClubStatus.REJECTED);
        assertThat(club.getRejectionReason()).isEqualTo("Documento ilegible");
        ArgumentCaptor<ClubReview> captor = ArgumentCaptor.forClass(ClubReview.class);
        verify(clubReviewRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("Documento ilegible");
    }

    @Test
    @DisplayName("resubmitClub: solo un club REJECTED puede reenviarse")
    void resubmitClub_NotRejected_ThrowsException() {
        actingAs(ADMIN_ID, Role.ROLE_USER);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.PENDING)));

        assertThatThrownBy(() -> clubService.resubmitClub(CLUB_ID, new ClubResubmitRequestDTO()))
                .isInstanceOf(ConflictException.class);

        verify(clubRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("resubmitClub: vuelve a PENDING, reemplaza el documento y no borra el historial")
    void resubmitClub_Rejected_BackToPending() {
        actingAs(ADMIN_ID, Role.ROLE_USER);
        Club club = club(ClubStatus.REJECTED);
        club.setRejectionReason("Documento ilegible");
        ClubResubmitRequestDTO dto = ClubResubmitRequestDTO.builder()
                .affiliationDocumentUrl("https://docs.test/nuevo.pdf").build();

        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club));

        clubService.resubmitClub(CLUB_ID, dto);

        assertThat(club.getStatus()).isEqualTo(ClubStatus.PENDING);
        assertThat(club.getRejectionReason()).isNull();
        assertThat(club.getAffiliationDocumentUrl()).isEqualTo("https://docs.test/nuevo.pdf");
        assertThat(club.getId()).isEqualTo(CLUB_ID);
        verifyNoInteractions(clubReviewRepository);
    }

    @Test
    @DisplayName("replaceAffiliationDocument: un club APPROVED vuelve a PENDING para re-verificación")
    void replaceAffiliationDocument_Approved_GoesBackToPending() {
        actingAs(ADMIN_ID, Role.ROLE_USER);
        Club club = club(ClubStatus.APPROVED);
        ClubAffiliationDocumentRequestDTO dto = ClubAffiliationDocumentRequestDTO.builder()
                .affiliationDocumentUrl("https://docs.test/renovado.pdf").build();

        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club));

        clubService.replaceAffiliationDocument(CLUB_ID, dto);

        assertThat(club.getStatus()).isEqualTo(ClubStatus.PENDING);
        assertThat(club.canOrganizeTournaments()).isFalse();
        assertThat(club.getAffiliationDocumentUrl()).isEqualTo("https://docs.test/renovado.pdf");
    }

    @Test
    @DisplayName("updateClub: solo el administrador del club puede modificarlo")
    void updateClub_NotClubAdmin_ThrowsException() {
        actingAs(OTHER_PLAYER_ID, Role.ROLE_USER);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.APPROVED)));

        assertThatThrownBy(() -> clubService.updateClub(CLUB_ID, new ClubUpdateRequestDTO()))
                .isInstanceOf(UnauthorizedActionException.class);

        verify(clubRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("getClubReview: otro jugador no puede ver el motivo privado de rechazo")
    void getClubReview_OtherPlayer_ThrowsException() {
        actingAs(OTHER_PLAYER_ID, Role.ROLE_USER);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.REJECTED)));
        when(playerRepository.findById(OTHER_PLAYER_ID)).thenReturn(Optional.of(player(OTHER_PLAYER_ID, Role.ROLE_USER)));

        assertThatThrownBy(() -> clubService.getClubReview(CLUB_ID))
                .isInstanceOf(UnauthorizedActionException.class);
    }

    @Test
    @DisplayName("transferAdministration: el nuevo administrador pasa a ser el admin del club")
    void transferAdministration_Success_ChangesClubAdmin() {
        actingAs(ADMIN_ID, Role.ROLE_USER);
        Club club = club(ClubStatus.APPROVED);
        Player newAdmin = player(OTHER_PLAYER_ID, Role.ROLE_USER);
        ClubAdminTransferRequestDTO dto = ClubAdminTransferRequestDTO.builder().newAdminPlayerId(OTHER_PLAYER_ID).build();

        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club));
        when(clubMembershipService.transferAdminRole(club, OTHER_PLAYER_ID)).thenReturn(newAdmin);

        clubService.transferAdministration(CLUB_ID, dto);

        assertThat(club.getAdmin()).isEqualTo(newAdmin);
        assertThat(newAdmin.getRole()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    @DisplayName("transferAdministration: no transfiere a un jugador que ya administra otro club en curso")
    void transferAdministration_NewAdminManagesAnotherClub_ThrowsException() {
        actingAs(ADMIN_ID, Role.ROLE_USER);
        Club club = club(ClubStatus.APPROVED);
        ClubAdminTransferRequestDTO dto = ClubAdminTransferRequestDTO.builder().newAdminPlayerId(OTHER_PLAYER_ID).build();

        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club));
        when(clubRepository.existsByAdminIdAndStatusInAndIdNot(OTHER_PLAYER_ID, ClubStatus.ACTIVE_STATUSES, CLUB_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> clubService.transferAdministration(CLUB_ID, dto))
                .isInstanceOf(ConflictException.class);

        verify(clubMembershipService, never()).transferAdminRole(any(), any());
        assertThat(club.getAdmin().getId()).isEqualTo(ADMIN_ID);
    }

    @Test
    @DisplayName("transferAdministration: un club que no está APPROVED no transfiere su administración")
    void transferAdministration_ClubNotApproved_ThrowsException() {
        actingAs(ADMIN_ID, Role.ROLE_USER);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(ClubStatus.PENDING)));

        assertThatThrownBy(() -> clubService.transferAdministration(CLUB_ID,
                ClubAdminTransferRequestDTO.builder().newAdminPlayerId(OTHER_PLAYER_ID).build()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("resubmitClub: no reenvía si el administrador ya gestiona otro club aprobado")
    void resubmitClub_AdminManagesAnotherActiveClub_ThrowsException() {
        actingAs(ADMIN_ID, Role.ROLE_USER);
        Club club = club(ClubStatus.REJECTED);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club));
        when(clubRepository.existsByAdminIdAndStatusInAndIdNot(ADMIN_ID, ClubStatus.ACTIVE_STATUSES, CLUB_ID)).thenReturn(true);

        assertThatThrownBy(() -> clubService.resubmitClub(CLUB_ID, new ClubResubmitRequestDTO()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("administra otro club");
        assertThat(club.getStatus()).isEqualTo(ClubStatus.REJECTED);
    }

    @Test
    @DisplayName("getApprovedClubs: pagina en la base de datos solo clubes APPROVED, ordenados por nombre y con tamaño limitado")
    void getApprovedClubs_PaginatesApprovedClubsInDatabase() {
        when(clubRepository.findByStatus(eq(ClubStatus.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(club(ClubStatus.APPROVED)), PageRequest.of(0, 10), 23));

        PageResponseDTO<ClubResponseDTO> result = clubService.getApprovedClubs(0, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clubRepository).findByStatus(eq(ClubStatus.APPROVED), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(PageRequestFactory.MAX_SIZE);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("name").ascending());
        assertThat(result.getTotalElements()).isEqualTo(23);
        assertThat(result.getContent()).hasSize(1);
        verify(clubRepository, never()).findAll();
    }

    @Test
    @DisplayName("getPendingClubs: mantiene el permiso de administrador general antes de consultar")
    void getPendingClubs_NotSystemAdmin_ThrowsBeforeQuerying() {
        actingAs(OTHER_PLAYER_ID, Role.ROLE_USER);
        when(playerRepository.findById(OTHER_PLAYER_ID)).thenReturn(Optional.of(player(OTHER_PLAYER_ID, Role.ROLE_USER)));

        assertThatThrownBy(() -> clubService.getPendingClubs(0, 10))
                .isInstanceOf(UnauthorizedActionException.class);

        verify(clubRepository, never()).findByStatus(any(), any());
    }

    @Test
    @DisplayName("getPendingClubs: pagina los clubes PENDING del más antiguo al más reciente")
    void getPendingClubs_PaginatesPendingClubs() {
        actingAs(SYSTEM_ADMIN_ID, Role.ROLE_SYSTEM_ADMIN);
        when(playerRepository.findById(SYSTEM_ADMIN_ID)).thenReturn(Optional.of(player(SYSTEM_ADMIN_ID, Role.ROLE_SYSTEM_ADMIN)));
        when(clubRepository.findByStatus(eq(ClubStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 5));

        clubService.getPendingClubs(1, 5);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clubRepository).findByStatus(eq(ClubStatus.PENDING), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("updatedAt").ascending());
    }
}

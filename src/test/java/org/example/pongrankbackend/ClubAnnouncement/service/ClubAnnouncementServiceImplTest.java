package org.example.pongrankbackend.ClubAnnouncement.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.ClubAnnouncement.ClubAnnouncement;
import org.example.pongrankbackend.ClubAnnouncement.dto.ClubAnnouncementRequestDTO;
import org.example.pongrankbackend.ClubAnnouncement.dto.ClubAnnouncementResponseDTO;
import org.example.pongrankbackend.ClubAnnouncement.repository.ClubAnnouncementRepository;
import org.example.pongrankbackend.ClubMembership.ClubMembership;
import org.example.pongrankbackend.ClubMembership.ClubMembershipStatus;
import org.example.pongrankbackend.ClubMembership.repository.ClubMembershipRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubAnnouncementServiceImplTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long MEMBER_ID = 3L;
    private static final Long OUTSIDER_ID = 9L;
    private static final Long CLUB_ID = 10L;

    @Mock
    private ClubAnnouncementRepository announcementRepository;

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private ClubMembershipRepository clubMembershipRepository;

    @InjectMocks
    private ClubAnnouncementServiceImpl service;

    private Player player(Long id) {
        return Player.builder().id(id).name("Jugador " + id).build();
    }

    private Club club() {
        return Club.builder().id(CLUB_ID).name("Club Lima").admin(player(ADMIN_ID)).build();
    }

    private ClubAnnouncementRequestDTO request() {
        return new ClubAnnouncementRequestDTO("  Torneo interno  ", "  El sábado a las 5 pm.  ");
    }

    private void actingAs(Long playerId) {
        CustomUserDetails userDetails = new CustomUserDetails(
                Player.builder().id(playerId).name("Jugador " + playerId).role(Role.ROLE_USER).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("publish: el administrador del club publica un aviso")
    void publish_ByAdmin_SavesAnnouncement() {
        actingAs(ADMIN_ID);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club()));
        when(announcementRepository.save(any(ClubAnnouncement.class))).thenAnswer(inv -> {
            ClubAnnouncement a = inv.getArgument(0);
            a.setId(5L);
            a.setCreatedAt(LocalDateTime.now());
            return a;
        });

        ClubAnnouncementResponseDTO response = service.publish(CLUB_ID, request());

        ArgumentCaptor<ClubAnnouncement> captor = ArgumentCaptor.forClass(ClubAnnouncement.class);
        verify(announcementRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Torneo interno");
        assertThat(captor.getValue().getBody()).isEqualTo("El sábado a las 5 pm.");
        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getClubId()).isEqualTo(CLUB_ID);
        assertThat(response.getAuthorName()).isEqualTo("Jugador " + ADMIN_ID);
    }

    @Test
    @DisplayName("publish: un miembro que no es administrador no puede publicar")
    void publish_ByNonAdmin_ThrowsException() {
        actingAs(MEMBER_ID);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club()));

        assertThatThrownBy(() -> service.publish(CLUB_ID, request()))
                .isInstanceOf(UnauthorizedActionException.class);
        verify(announcementRepository, never()).save(any());
    }

    @Test
    @DisplayName("publish: club inexistente lanza ResourceNotFoundException")
    void publish_ClubNotFound_ThrowsException() {
        actingAs(ADMIN_ID);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(CLUB_ID, request()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("list: un miembro aprobado ve los avisos del club")
    void list_ByApprovedMember_ReturnsAnnouncements() {
        actingAs(MEMBER_ID);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club()));
        when(clubMembershipRepository.findByPlayerIdAndClubIdAndStatus(MEMBER_ID, CLUB_ID, ClubMembershipStatus.APPROVED))
                .thenReturn(Optional.of(ClubMembership.builder().id(1L).build()));
        ClubAnnouncement announcement = ClubAnnouncement.builder()
                .id(5L).club(club()).author(player(ADMIN_ID))
                .title("Torneo interno").body("El sábado").createdAt(LocalDateTime.now()).build();
        when(announcementRepository.findByClubId(org.mockito.ArgumentMatchers.eq(CLUB_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(announcement)));

        PageResponseDTO<ClubAnnouncementResponseDTO> response = service.list(CLUB_ID, 0, 10);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getTitle()).isEqualTo("Torneo interno");
    }

    @Test
    @DisplayName("list: quien no es miembro ni administrador no ve los avisos")
    void list_ByOutsider_ThrowsException() {
        actingAs(OUTSIDER_ID);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club()));
        when(clubMembershipRepository.findByPlayerIdAndClubIdAndStatus(OUTSIDER_ID, CLUB_ID, ClubMembershipStatus.APPROVED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(CLUB_ID, 0, 10))
                .isInstanceOf(UnauthorizedActionException.class);
        verify(announcementRepository, never()).findByClubId(any(), any());
    }
}

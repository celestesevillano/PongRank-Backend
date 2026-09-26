package org.example.pongrankbackend.Player.service;

import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.ClubMembership.ClubMembership;
import org.example.pongrankbackend.ClubMembership.ClubMembershipStatus;
import org.example.pongrankbackend.ClubMembership.repository.ClubMembershipRepository;
import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.CommunityMembership.CommunityMembership;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.CommunityMembership.MembershipStatus;
import org.example.pongrankbackend.CommunityMembership.repository.CommunityMembershipRepository;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.service.MembershipService;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Player.dto.DeleteAccountRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.dto.PlayerUpdateRequestDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.auth.dto.AuthResponseDTO;
import org.example.pongrankbackend.auth.service.AuthService;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.EmailAlreadyExistsException;
import org.example.pongrankbackend.common.exception.InvalidCredentialsException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.email.service.EmailService;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerServiceImplTest {

    private static final Long PLAYER_ID = 1L;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private ClubMembershipRepository clubMembershipRepository;

    @Mock
    private CommunityMembershipRepository communityMembershipRepository;

    @Mock
    private MembershipService membershipService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private AuthService authService;

    @InjectMocks
    private PlayerServiceImpl playerService;

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
    @DisplayName("registerPlayer: delega en authService.register y retorna el PlayerResponseDTO")
    void registerPlayer_Successful() {
        // Arrange
        PlayerRegisterRequestDTO dto = PlayerRegisterRequestDTO.builder()
                .name("Carlos Gomez")
                .email("carlos.gomez@domain.com")
                .password("Password123")
                .build();

        PlayerResponseDTO expectedResponse = PlayerResponseDTO.builder()
                .id(1L)
                .name("Carlos Gomez")
                .email("carlos.gomez@domain.com")
                .role(Role.ROLE_USER)
                .status(PlayerStatus.ACTIVE)
                .build();

        AuthResponseDTO authResponse = AuthResponseDTO.builder()
                .token("access_token")
                .refreshToken("refresh_token")
                .player(expectedResponse)
                .build();

        when(authService.register(dto)).thenReturn(authResponse);

        // Act
        PlayerResponseDTO actualResponse = playerService.registerPlayer(dto);

        // Assert
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.getId()).isEqualTo(1L);
        assertThat(actualResponse.getEmail()).isEqualTo("carlos.gomez@domain.com");
        verify(authService).register(dto);
    }

    @Test
    @DisplayName("registerPlayer: propaga excepción cuando el email ya existe en authService")
    void registerPlayer_DuplicateEmail_ThrowsException() {
        // Arrange
        PlayerRegisterRequestDTO dto = PlayerRegisterRequestDTO.builder()
                .name("Carlos Gomez")
                .email("carlos.gomez@domain.com")
                .password("Password123")
                .build();

        when(authService.register(dto))
                .thenThrow(new EmailAlreadyExistsException("El email 'carlos.gomez@domain.com' ya se encuentra registrado"));

        // Act & Assert
        assertThatThrownBy(() -> playerService.registerPlayer(dto))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("ya se encuentra registrado");

        verify(authService).register(dto);
    }

    @Test
    @DisplayName("getPlayerById: devuelve PlayerResponseDTO cuando el jugador existe")
    void getPlayerById_Success() {
        // Arrange
        Long playerId = 1L;
        Player player = Player.builder().id(playerId).name("Carlos Gomez").email("carlos@domain.com").build();
        PlayerResponseDTO responseDto = PlayerResponseDTO.builder().id(playerId).name("Carlos Gomez").build();

        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(modelMapper.map(player, PlayerResponseDTO.class)).thenReturn(responseDto);

        // Act
        PlayerResponseDTO result = playerService.getPlayerById(playerId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(playerId);
        verify(playerRepository).findById(playerId);
    }

    @Test
    @DisplayName("getPlayerById: lanza ResourceNotFoundException cuando el jugador no existe")
    void getPlayerById_NotFound_ThrowsException() {
        // Arrange
        Long playerId = 99L;
        when(playerRepository.findById(playerId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> playerService.getPlayerById(playerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Jugador no encontrado con ID: 99");

        verify(modelMapper, never()).map(any(), any());
    }

    @Test
    @DisplayName("getPlayerSummaryById: devuelve PlayerSummaryDTO cuando el jugador existe")
    void getPlayerSummaryById_Success() {
        // Arrange
        Long playerId = 1L;
        Player player = Player.builder().id(playerId).name("Carlos Gomez").ratingGlicko(1500.0).build();
        PlayerSummaryDTO summaryDto = PlayerSummaryDTO.builder().id(playerId).name("Carlos Gomez").ratingGlicko(1500.0).build();

        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(modelMapper.map(player, PlayerSummaryDTO.class)).thenReturn(summaryDto);

        // Act
        PlayerSummaryDTO result = playerService.getPlayerSummaryById(playerId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(playerId);
        assertThat(result.getName()).isEqualTo("Carlos Gomez");
        verify(playerRepository).findById(playerId);
    }

    @Test
    @DisplayName("updatePlayer: actualiza campos permitidos sin alterar el jugador cuando el id existe")
    void updatePlayer_Success() {
        // Arrange
        Long playerId = 1L;
        Player existingPlayer = Player.builder()
                .id(playerId)
                .name("Carlos Gomez")
                .email("carlos@domain.com")
                .whatsapp("999111222")
                .shareContact(false)
                .build();

        PlayerUpdateRequestDTO updateDto = PlayerUpdateRequestDTO.builder()
                .name("Carlos Gomez Actualizado")
                .whatsapp("999333444")
                .shareContact(true)
                .build();

        Player updatedPlayer = Player.builder()
                .id(playerId)
                .name("Carlos Gomez Actualizado")
                .email("carlos@domain.com")
                .whatsapp("999333444")
                .shareContact(true)
                .build();

        PlayerResponseDTO expectedResponse = PlayerResponseDTO.builder()
                .id(playerId)
                .name("Carlos Gomez Actualizado")
                .whatsapp("999333444")
                .shareContact(true)
                .build();

        doNothing().when(modelMapper).map(updateDto, existingPlayer);
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(existingPlayer));
        when(playerRepository.save(existingPlayer)).thenReturn(updatedPlayer);
        when(modelMapper.map(updatedPlayer, PlayerResponseDTO.class)).thenReturn(expectedResponse);

        // Act
        PlayerResponseDTO result = playerService.updatePlayer(playerId, updateDto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Carlos Gomez Actualizado");

        verify(modelMapper).map(updateDto, existingPlayer);
        verify(playerRepository).save(existingPlayer);
    }

    @Test
    @DisplayName("updatePlayer: campos null en DTO no sobrescriben valores existentes")
    void updatePlayer_NullFieldsIgnored() {
        // Arrange
        Long playerId = 1L;
        Player existingPlayer = Player.builder()
                .id(playerId)
                .name("Carlos Gomez")
                .whatsapp("999111222")
                .shareContact(true)
                .categoryFdptm("Primera")
                .build();

        PlayerUpdateRequestDTO emptyUpdateDto = PlayerUpdateRequestDTO.builder().build();

        doNothing().when(modelMapper).map(emptyUpdateDto, existingPlayer);
        when(playerRepository.findById(playerId)).thenReturn(Optional.of(existingPlayer));
        when(playerRepository.save(existingPlayer)).thenReturn(existingPlayer);
        when(modelMapper.map(existingPlayer, PlayerResponseDTO.class)).thenReturn(PlayerResponseDTO.builder().id(playerId).name("Carlos Gomez").build());

        // Act
        playerService.updatePlayer(playerId, emptyUpdateDto);

        // Assert
        verify(modelMapper).map(emptyUpdateDto, existingPlayer);
        verify(playerRepository).save(existingPlayer);
    }

    // ----- deleteAccount -----

    private Player playerWithPassword(String encodedPassword) {
        return Player.builder().id(PLAYER_ID).name("Carlos Gomez").email("carlos@domain.com")
                .password(encodedPassword).build();
    }

    private DeleteAccountRequestDTO deleteRequest(String password) {
        return DeleteAccountRequestDTO.builder().password(password).build();
    }

    @Test
    @DisplayName("deleteAccount: lanza InvalidCredentialsException cuando la contraseña no coincide")
    void deleteAccount_WrongPassword_ThrowsException() {
        actingAs(PLAYER_ID);
        Player player = playerWithPassword("hashed");
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> playerService.deleteAccount(deleteRequest("wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(playerRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteAccount: no elimina si el jugador administra un club activo")
    void deleteAccount_AdministersActiveClub_ThrowsException() {
        actingAs(PLAYER_ID);
        Player player = playerWithPassword("hashed");
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(clubRepository.existsByAdminIdAndStatusIn(PLAYER_ID, ClubStatus.ACTIVE_STATUSES)).thenReturn(true);

        assertThatThrownBy(() -> playerService.deleteAccount(deleteRequest("correct")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("transferir la administración");

        verify(playerRepository, never()).save(any());
        verify(emailService, never()).sendAccountDeletedEmail(any(), any());
    }

    @Test
    @DisplayName("deleteAccount: no elimina si es el último admin activo de una comunidad")
    void deleteAccount_LastCommunityAdmin_ThrowsException() {
        actingAs(PLAYER_ID);
        Player player = playerWithPassword("hashed");
        Community community = Community.builder().id(20L).name("UTEC").build();
        CommunityMembership adminMembership = CommunityMembership.builder()
                .id(1L).player(player).community(community)
                .role(CommunityRole.COMMUNITY_ADMIN).status(MembershipStatus.ACTIVE).build();

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(clubRepository.existsByAdminIdAndStatusIn(PLAYER_ID, ClubStatus.ACTIVE_STATUSES)).thenReturn(false);
        when(communityMembershipRepository.findByPlayerIdWithCommunity(PLAYER_ID, MembershipStatus.ACTIVE))
                .thenReturn(List.of(adminMembership));
        when(communityMembershipRepository.countByCommunityIdAndRoleAndStatus(
                20L, CommunityRole.COMMUNITY_ADMIN, MembershipStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> playerService.deleteAccount(deleteRequest("correct")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("UTEC");

        verify(playerRepository, never()).save(any());
        verify(emailService, never()).sendAccountDeletedEmail(any(), any());
    }

    @Test
    @DisplayName("deleteAccount: elimina la cuenta, envía el correo y desactiva las membresías activas")
    void deleteAccount_Success_DeactivatesMembershipsAndSendsEmail() {
        actingAs(PLAYER_ID);
        Player player = playerWithPassword("hashed");
        Community community = Community.builder().id(20L).name("UTEC").build();
        CommunityMembership memberMembership = CommunityMembership.builder()
                .id(1L).player(player).community(community)
                .role(CommunityRole.MEMBER).status(MembershipStatus.ACTIVE).build();
        ClubMembership clubMembership = ClubMembership.builder()
                .id(2L).player(player).status(ClubMembershipStatus.APPROVED).build();

        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(clubRepository.existsByAdminIdAndStatusIn(PLAYER_ID, ClubStatus.ACTIVE_STATUSES)).thenReturn(false);
        when(communityMembershipRepository.findByPlayerIdWithCommunity(PLAYER_ID, MembershipStatus.ACTIVE))
                .thenReturn(List.of(memberMembership));
        when(membershipService.getActivePlan(PLAYER_ID)).thenReturn(MembershipPlan.PRO);
        Page<ClubMembership> clubMembershipPage = new PageImpl<>(List.of(clubMembership));
        when(clubMembershipRepository.findByPlayerId(eq(PLAYER_ID), any())).thenReturn(clubMembershipPage);
        when(passwordEncoder.encode(any())).thenReturn("new-hash");

        playerService.deleteAccount(deleteRequest("correct"));

        assertThat(memberMembership.getStatus()).isEqualTo(MembershipStatus.INACTIVE);
        assertThat(memberMembership.getLeftAt()).isNotNull();
        assertThat(clubMembership.getStatus()).isEqualTo(ClubMembershipStatus.LEFT);
        assertThat(clubMembership.getLeftAt()).isNotNull();
        assertThat(player.getStatus()).isEqualTo(PlayerStatus.DELETED);
        assertThat(player.getEmail()).startsWith("carlos@domain.com.deleted.1.");
        verify(emailService).sendAccountDeletedEmail(player, MembershipPlan.PRO);
        verify(playerRepository).save(player);
    }
}

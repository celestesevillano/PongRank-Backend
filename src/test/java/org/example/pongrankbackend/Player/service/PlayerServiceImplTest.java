package org.example.pongrankbackend.Player.service;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.dto.PlayerUpdateRequestDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.auth.dto.AuthResponseDTO;
import org.example.pongrankbackend.auth.service.AuthService;
import org.example.pongrankbackend.common.exception.EmailAlreadyExistsException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerServiceImplTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private AuthService authService;

    @InjectMocks
    private PlayerServiceImpl playerService;

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
}

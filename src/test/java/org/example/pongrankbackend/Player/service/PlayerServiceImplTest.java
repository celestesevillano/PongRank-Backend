package org.example.pongrankbackend.Player.service;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.dto.PlayerUpdateRequestDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.EmailAlreadyExistsException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerServiceImplTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PlayerServiceImpl playerService;

    @Test
    @DisplayName("registerPlayer: registra un jugador exitosamente con email normalizado, password codificado y valores por defecto")
    void registerPlayer_Successful() {
        // Arrange
        PlayerRegisterRequestDTO dto = PlayerRegisterRequestDTO.builder()
                .name("Carlos Gomez")
                .email("  CARLOS.GOMEZ@Domain.COM  ")
                .password("Password123")
                .whatsapp("999111222")
                .shareContact(null)
                .categoryFdptm("Primera")
                .federatedDeclared(null)
                .build();

        Player mappedPlayer = Player.builder()
                .name("Carlos Gomez")
                .email("  CARLOS.GOMEZ@Domain.COM  ")
                .password("Password123")
                .whatsapp("999111222")
                .categoryFdptm("Primera")
                .build();

        Player savedPlayer = Player.builder()
                .id(1L)
                .name("Carlos Gomez")
                .email("carlos.gomez@domain.com")
                .password("encoded_Password123")
                .role(Role.ROLE_USER)
                .status(PlayerStatus.ACTIVE)
                .shareContact(false)
                .federatedDeclared(false)
                .ratingGlicko(1500.0)
                .ratingDeviation(350.0)
                .volatility(0.06)
                .build();

        PlayerResponseDTO expectedResponse = PlayerResponseDTO.builder()
                .id(1L)
                .name("Carlos Gomez")
                .email("carlos.gomez@domain.com")
                .role(Role.ROLE_USER)
                .status(PlayerStatus.ACTIVE)
                .shareContact(false)
                .federatedDeclared(false)
                .build();

        when(playerRepository.existsByEmail("carlos.gomez@domain.com")).thenReturn(false);
        when(modelMapper.map(dto, Player.class)).thenReturn(mappedPlayer);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded_Password123");
        when(playerRepository.save(any(Player.class))).thenReturn(savedPlayer);
        when(modelMapper.map(savedPlayer, PlayerResponseDTO.class)).thenReturn(expectedResponse);

        // Act
        PlayerResponseDTO actualResponse = playerService.registerPlayer(dto);

        // Assert
        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.getId()).isEqualTo(1L);
        assertThat(actualResponse.getEmail()).isEqualTo("carlos.gomez@domain.com");

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(playerCaptor.capture());
        Player playerToSave = playerCaptor.getValue();

        assertThat(playerToSave.getEmail()).isEqualTo("carlos.gomez@domain.com");
        assertThat(playerToSave.getPassword()).isEqualTo("encoded_Password123");
        assertThat(playerToSave.getPassword()).isNotEqualTo("Password123");
        assertThat(playerToSave.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(playerToSave.getStatus()).isEqualTo(PlayerStatus.ACTIVE);
        assertThat(playerToSave.getShareContact()).isFalse();
        assertThat(playerToSave.getFederatedDeclared()).isFalse();

        verify(passwordEncoder).encode("Password123");
        verify(playerRepository).existsByEmail("carlos.gomez@domain.com");
    }

    @Test
    @DisplayName("registerPlayer: lanza excepción cuando el email ya existe y no guarda en repository")
    void registerPlayer_DuplicateEmail_ThrowsException() {
        // Arrange
        PlayerRegisterRequestDTO dto = PlayerRegisterRequestDTO.builder()
                .name("Carlos Gomez")
                .email("carlos.gomez@domain.com")
                .password("Password123")
                .build();

        when(playerRepository.existsByEmail("carlos.gomez@domain.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> playerService.registerPlayer(dto))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("ya se encuentra registrado");

        verify(playerRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
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

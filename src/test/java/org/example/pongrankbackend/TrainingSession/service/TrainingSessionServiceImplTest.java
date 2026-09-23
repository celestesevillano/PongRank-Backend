package org.example.pongrankbackend.TrainingSession.service;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.TrainingSession.TrainingSession;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionCreateDTO;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionResponseDTO;
import org.example.pongrankbackend.TrainingSession.mapper.TrainingSessionMapper;
import org.example.pongrankbackend.TrainingSession.repository.TrainingSessionRepository;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingSessionServiceImplTest {

    @Mock
    private TrainingSessionRepository trainingSessionRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private TrainingSessionMapper trainingSessionMapper;

    @InjectMocks
    private TrainingSessionServiceImpl trainingSessionService;

    @Test
    @DisplayName("registerSession: guarda la sesión asociada al jugador cuando el jugador existe")
    void registerSession_Success() {
        // Arrange
        Long playerId = 1L;
        Player player = Player.builder().id(playerId).name("Jugador A").build();
        TrainingSessionCreateDTO dto = buildCreateDto(80.0);

        TrainingSession mappedSession = TrainingSession.builder()
                .swingVelocityMax(dto.getSwingVelocityMax())
                .postureScore(dto.getPostureScore())
                .durationSeconds(dto.getDurationSeconds())
                .build();

        TrainingSession savedSession = TrainingSession.builder()
                .id(10L)
                .player(player)
                .swingVelocityMax(dto.getSwingVelocityMax())
                .postureScore(dto.getPostureScore())
                .durationSeconds(dto.getDurationSeconds())
                .build();

        TrainingSessionResponseDTO responseDto = TrainingSessionResponseDTO.builder()
                .id(10L)
                .postureScore(80.0)
                .build();

        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(trainingSessionRepository.findTopByPlayerIdOrderByPostureScoreDesc(playerId)).thenReturn(Optional.empty());
        when(trainingSessionMapper.toEntity(dto)).thenReturn(mappedSession);
        when(trainingSessionRepository.save(any(TrainingSession.class))).thenReturn(savedSession);
        when(trainingSessionMapper.toDto(savedSession)).thenReturn(responseDto);

        // Act
        TrainingSessionResponseDTO result = trainingSessionService.registerSession(playerId, dto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getPostureScore()).isEqualTo(80.0);

        ArgumentCaptor<TrainingSession> captor = ArgumentCaptor.forClass(TrainingSession.class);
        verify(trainingSessionRepository).save(captor.capture());
        TrainingSession created = captor.getValue();

        assertThat(created.getPlayer()).isSameAs(player);
        assertThat(created.getPostureScore()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("registerSession: lanza ResourceNotFoundException cuando el jugador no existe")
    void registerSession_PlayerNotFound_ThrowsException() {
        // Arrange
        Long playerId = 99L;
        TrainingSessionCreateDTO dto = buildCreateDto(80.0);

        when(playerRepository.findById(playerId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> trainingSessionService.registerSession(playerId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Jugador no encontrado");

        verify(trainingSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerSession: marca newPersonalBest=true cuando el postureScore supera el récord anterior")
    void registerSession_BetterThanPreviousBest_MarksNewPersonalBest() {
        // Arrange
        Long playerId = 1L;
        stubRegisterSession(playerId, 90.0, Optional.of(TrainingSession.builder().postureScore(75.0).build()));

        // Act
        TrainingSessionResponseDTO result = trainingSessionService.registerSession(playerId, buildCreateDto(90.0));

        // Assert
        assertThat(result.isNewPersonalBest()).isTrue();
    }

    @Test
    @DisplayName("registerSession: marca newPersonalBest=true cuando es la primera sesión del jugador")
    void registerSession_FirstSession_MarksNewPersonalBest() {
        // Arrange
        Long playerId = 1L;
        stubRegisterSession(playerId, 50.0, Optional.empty());

        // Act
        TrainingSessionResponseDTO result = trainingSessionService.registerSession(playerId, buildCreateDto(50.0));

        // Assert
        assertThat(result.isNewPersonalBest()).isTrue();
    }

    @Test
    @DisplayName("registerSession: marca newPersonalBest=false cuando el postureScore no supera el récord anterior")
    void registerSession_NotBetterThanPreviousBest_DoesNotMarkNewPersonalBest() {
        // Arrange
        Long playerId = 1L;
        stubRegisterSession(playerId, 70.0, Optional.of(TrainingSession.builder().postureScore(85.0).build()));

        // Act
        TrainingSessionResponseDTO result = trainingSessionService.registerSession(playerId, buildCreateDto(70.0));

        // Assert
        assertThat(result.isNewPersonalBest()).isFalse();
    }

    @Test
    @DisplayName("getHistoryByPlayer: devuelve las sesiones del jugador ordenadas de la más reciente a la más antigua")
    void getHistoryByPlayer_Success() {
        // Arrange
        Long playerId = 1L;
        TrainingSession newest = TrainingSession.builder().id(200L).createdAt(LocalDateTime.of(2026, 9, 20, 10, 0)).build();
        TrainingSession oldest = TrainingSession.builder().id(100L).createdAt(LocalDateTime.of(2026, 9, 10, 10, 0)).build();

        TrainingSessionResponseDTO newestDto = TrainingSessionResponseDTO.builder().id(200L).createdAt(newest.getCreatedAt()).build();
        TrainingSessionResponseDTO oldestDto = TrainingSessionResponseDTO.builder().id(100L).createdAt(oldest.getCreatedAt()).build();

        when(playerRepository.existsById(playerId)).thenReturn(true);
        when(trainingSessionRepository.findByPlayerIdOrderByCreatedAtDesc(playerId)).thenReturn(List.of(newest, oldest));
        when(trainingSessionMapper.toDto(newest)).thenReturn(newestDto);
        when(trainingSessionMapper.toDto(oldest)).thenReturn(oldestDto);

        // Act
        List<TrainingSessionResponseDTO> result = trainingSessionService.getHistoryByPlayer(playerId);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(TrainingSessionResponseDTO::getId).containsExactly(200L, 100L);
        verify(trainingSessionRepository).findByPlayerIdOrderByCreatedAtDesc(playerId);
    }

    @Test
    @DisplayName("getHistoryByPlayer: lanza ResourceNotFoundException cuando el jugador no existe")
    void getHistoryByPlayer_PlayerNotFound_ThrowsException() {
        // Arrange
        Long playerId = 99L;

        when(playerRepository.existsById(playerId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> trainingSessionService.getHistoryByPlayer(playerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Jugador no encontrado");

        verify(trainingSessionRepository, never()).findByPlayerIdOrderByCreatedAtDesc(any());
    }

    private TrainingSessionCreateDTO buildCreateDto(Double postureScore) {
        return TrainingSessionCreateDTO.builder()
                .swingVelocityMax(12.5)
                .postureScore(postureScore)
                .durationSeconds(300)
                .build();
    }

    private void stubRegisterSession(Long playerId, Double postureScore, Optional<TrainingSession> previousBest) {
        Player player = Player.builder().id(playerId).build();
        TrainingSession mappedSession = TrainingSession.builder().postureScore(postureScore).build();
        TrainingSession savedSession = TrainingSession.builder().id(10L).player(player).postureScore(postureScore).build();

        when(playerRepository.findById(playerId)).thenReturn(Optional.of(player));
        when(trainingSessionRepository.findTopByPlayerIdOrderByPostureScoreDesc(playerId)).thenReturn(previousBest);
        when(trainingSessionMapper.toEntity(any(TrainingSessionCreateDTO.class))).thenReturn(mappedSession);
        when(trainingSessionRepository.save(any(TrainingSession.class))).thenReturn(savedSession);
        when(trainingSessionMapper.toDto(savedSession)).thenReturn(TrainingSessionResponseDTO.builder().id(10L).postureScore(postureScore).build());
    }
}

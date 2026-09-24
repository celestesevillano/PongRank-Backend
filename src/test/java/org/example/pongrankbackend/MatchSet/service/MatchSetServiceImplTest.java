package org.example.pongrankbackend.MatchSet.service;

import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.MatchSet.MatchSet;
import org.example.pongrankbackend.MatchSet.dto.MatchSetResponseDTO;
import org.example.pongrankbackend.MatchSet.repository.MatchSetRepository;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchSetServiceImplTest {

    @Mock
    private MatchSetRepository matchSetRepository;

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private MatchSetServiceImpl matchSetService;

    @Test
    @DisplayName("shouldReturnSetsByMatchIdWhenMatchExists")
    void shouldReturnSetsByMatchIdWhenMatchExists() {
        // Arrange
        Long matchId = 1L;
        MatchSet set1 = MatchSet.builder().id(10L).setNumber(1).scorePlayer1(11).scorePlayer2(7).build();
        MatchSet set2 = MatchSet.builder().id(11L).setNumber(2).scorePlayer1(9).scorePlayer2(11).build();

        when(matchRepository.existsById(matchId)).thenReturn(true);
        when(matchSetRepository.findByMatchIdOrderBySetNumberAsc(matchId)).thenReturn(List.of(set1, set2));

        // Act
        List<MatchSetResponseDTO> result = matchSetService.getSetsByMatchId(matchId);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSetNumber()).isEqualTo(1);
        assertThat(result.get(0).getWinnerPlayerNumber()).isEqualTo(1);
        assertThat(result.get(1).getSetNumber()).isEqualTo(2);
        assertThat(result.get(1).getWinnerPlayerNumber()).isEqualTo(2);

        verify(matchRepository).existsById(matchId);
        verify(matchSetRepository).findByMatchIdOrderBySetNumberAsc(matchId);
    }

    @Test
    @DisplayName("shouldThrowResourceNotFoundExceptionWhenMatchDoesNotExist")
    void shouldThrowResourceNotFoundExceptionWhenMatchDoesNotExist() {
        // Arrange
        Long matchId = 99L;
        when(matchRepository.existsById(matchId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> matchSetService.getSetsByMatchId(matchId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Partido no encontrado con ID: 99");

        verify(matchSetRepository, never()).findByMatchIdOrderBySetNumberAsc(any());
    }

    @Test
    @DisplayName("shouldReturnSetByIdWhenSetExists")
    void shouldReturnSetByIdWhenSetExists() {
        // Arrange
        Long setId = 100L;
        MatchSet set = MatchSet.builder().id(setId).setNumber(1).scorePlayer1(12).scorePlayer2(10).build();

        when(matchSetRepository.findById(setId)).thenReturn(Optional.of(set));

        // Act
        MatchSetResponseDTO result = matchSetService.getSetById(setId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(setId);
        assertThat(result.getScorePlayer1()).isEqualTo(12);
        assertThat(result.getScorePlayer2()).isEqualTo(10);
        assertThat(result.getWinnerPlayerNumber()).isEqualTo(1);

        verify(matchSetRepository).findById(setId);
    }

    @Test
    @DisplayName("shouldThrowResourceNotFoundExceptionWhenSetDoesNotExist")
    void shouldThrowResourceNotFoundExceptionWhenSetDoesNotExist() {
        // Arrange
        Long setId = 999L;
        when(matchSetRepository.findById(setId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> matchSetService.getSetById(setId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Set no encontrado con ID: 999");
    }
}

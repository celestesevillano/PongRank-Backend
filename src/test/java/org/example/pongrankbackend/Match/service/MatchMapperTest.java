package org.example.pongrankbackend.Match.service;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.dto.MatchDetailResponseDTO;
import org.example.pongrankbackend.Match.dto.MatchResponseDTO;
import org.example.pongrankbackend.MatchSet.MatchSet;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MatchMapperTest {

    @Mock
    private ModelMapper modelMapper;

    private MatchMapper matchMapper;
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() {
        matchMapper = new MatchMapper(modelMapper);
        alice = Player.builder().id(1L).name("Alice").build();
        bob = Player.builder().id(2L).name("Bob").build();
        lenient().when(modelMapper.map(any(Player.class), org.mockito.ArgumentMatchers.eq(PlayerSummaryDTO.class)))
                .thenAnswer(inv -> {
                    Player p = inv.getArgument(0);
                    return PlayerSummaryDTO.builder().id(p.getId()).name(p.getName()).build();
                });
    }

    private MatchSet set(int number, int p1, int p2) {
        return MatchSet.builder().setNumber(number).scorePlayer1(p1).scorePlayer2(p2).build();
    }

    @Test
    @DisplayName("toResponseDTO: resume el marcador por sets y deduce el ganador de un partido confirmado")
    void toResponseDTO_ConfirmedMatch_SummarizesSetsAndWinner() {
        Match match = Match.builder()
                .id(10L).player1(alice).player2(bob).status(MatchStatus.CONFIRMED)
                .sets(new ArrayList<>(List.of(set(1, 11, 5), set(2, 8, 11), set(3, 11, 9))))
                .build();

        MatchResponseDTO dto = matchMapper.toResponseDTO(match);

        assertThat(dto.getScoreSummary()).isEqualTo("2 - 1");
        assertThat(dto.getWinnerId()).isEqualTo(1L);
        assertThat(dto.getPlayer2().getName()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("toResponseDTO: un walkover sin sets muestra W.O. como marcador")
    void toResponseDTO_Walkover_ShowsWoSummary() {
        Match match = Match.builder().id(11L).player1(alice).player2(bob).status(MatchStatus.WALKOVER).winner(bob).build();

        MatchResponseDTO dto = matchMapper.toResponseDTO(match);

        assertThat(dto.getScoreSummary()).isEqualTo("W.O.");
        assertThat(dto.getWinnerId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("toDetailResponseDTO: incluye los sets con el numero del jugador ganador de cada uno")
    void toDetailResponseDTO_IncludesSetsWithWinnerNumber() {
        Match match = Match.builder()
                .id(12L).player1(alice).player2(bob).status(MatchStatus.PROPOSED_P1)
                .disputeReason("motivo")
                .sets(new ArrayList<>(List.of(set(1, 11, 5), set(2, 8, 11))))
                .build();

        MatchDetailResponseDTO dto = matchMapper.toDetailResponseDTO(match);

        assertThat(dto.getSets()).hasSize(2);
        assertThat(dto.getSets().get(0).getWinnerPlayerNumber()).isEqualTo(1);
        assertThat(dto.getSets().get(1).getWinnerPlayerNumber()).isEqualTo(2);
        assertThat(dto.getDisputeReason()).isEqualTo("motivo");
    }
}

package org.example.pongrankbackend.Tournament.integration;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.dto.MatchDetailResponseDTO;
import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.Match.service.MatchService;
import org.example.pongrankbackend.MatchSet.dto.MatchSetResponseDTO;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProvisionalMatchIntegrationAdapterTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchService matchService;

    @InjectMocks
    private ProvisionalMatchIntegrationAdapter adapter;

    private Match matchWithId(long id) {
        Match match = new Match();
        match.setId(id);
        return match;
    }

    private MatchSetResponseDTO set(int number, int p1, int p2) {
        return MatchSetResponseDTO.builder().setNumber(number).scorePlayer1(p1).scorePlayer2(p2)
                .winnerPlayerNumber(p1 > p2 ? 1 : 2).build();
    }

    @Test
    @DisplayName("createMatch: guarda el partido en el módulo Match con ambos jugadores, el formato del torneo y estado CREATED")
    void createMatch_SavesMatchThroughMatchRepository() {
        Player p1 = Player.builder().id(1L).build();
        Player p2 = Player.builder().id(2L).build();
        when(matchRepository.save(any(Match.class))).thenAnswer(i -> i.getArgument(0));

        adapter.createMatch(p1, p2, MatchFormat.BO5);

        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());
        assertThat(captor.getValue().getPlayer1()).isEqualTo(p1);
        assertThat(captor.getValue().getPlayer2()).isEqualTo(p2);
        assertThat(captor.getValue().getFormat()).isEqualTo(MatchFormat.BO5);
        assertThat(captor.getValue().getStatus()).isEqualTo(MatchStatus.CREATED);
    }

    @Test
    @DisplayName("findConfirmedOutcome: usa el ganador y los sets que calcula MatchService")
    void findConfirmedOutcome_UsesMatchServiceResult() {
        when(matchService.getMatchById(7L)).thenReturn(MatchDetailResponseDTO.builder()
                .id(7L).status(MatchStatus.CONFIRMED).winnerId(2L)
                .player1(PlayerSummaryDTO.builder().id(1L).build())
                .player2(PlayerSummaryDTO.builder().id(2L).build())
                .sets(List.of(set(1, 11, 9), set(2, 8, 11), set(3, 10, 12), set(4, 5, 11)))
                .build());

        Optional<MatchOutcome> outcome = adapter.findConfirmedOutcome(matchWithId(7L));

        assertThat(outcome).isPresent();
        assertThat(outcome.get().player1Id()).isEqualTo(1L);
        assertThat(outcome.get().player2Id()).isEqualTo(2L);
        assertThat(outcome.get().winnerPlayerId()).isEqualTo(2L);
        assertThat(outcome.get().setsPlayer1()).isEqualTo(1);
        assertThat(outcome.get().setsPlayer2()).isEqualTo(3);
        assertThat(outcome.get().pointsPlayer1()).isEqualTo(34);
        assertThat(outcome.get().pointsPlayer2()).isEqualTo(43);
    }

    @Test
    @DisplayName("findConfirmedOutcome: un partido todavía no confirmado no produce resultado")
    void findConfirmedOutcome_NotConfirmed_Empty() {
        when(matchService.getMatchById(7L)).thenReturn(MatchDetailResponseDTO.builder()
                .id(7L).status(MatchStatus.PROPOSED_P1).winnerId(1L).sets(List.of(set(1, 11, 3))).build());

        assertThat(adapter.findConfirmedOutcome(matchWithId(7L))).isEmpty();
    }

    @Test
    @DisplayName("findConfirmedOutcome: sin partido vinculado no consulta a MatchService")
    void findConfirmedOutcome_NoMatch_Empty() {
        assertThat(adapter.findConfirmedOutcome(null)).isEmpty();
        verifyNoInteractions(matchService);
    }

    @Test
    @DisplayName("hasReportedScore: CREATED o CANCELLED no tienen marcador; PROPOSED/CONFIRMED/DISPUTED sí")
    void hasReportedScore_DependsOnMatchStatus() {
        when(matchService.getMatchById(7L)).thenReturn(
                MatchDetailResponseDTO.builder().id(7L).status(MatchStatus.CREATED).build(),
                MatchDetailResponseDTO.builder().id(7L).status(MatchStatus.CANCELLED).build(),
                MatchDetailResponseDTO.builder().id(7L).status(MatchStatus.PROPOSED_P1).build(),
                MatchDetailResponseDTO.builder().id(7L).status(MatchStatus.DISPUTED).build());

        assertThat(adapter.hasReportedScore(matchWithId(7L))).isFalse();
        assertThat(adapter.hasReportedScore(matchWithId(7L))).isFalse();
        assertThat(adapter.hasReportedScore(matchWithId(7L))).isTrue();
        assertThat(adapter.hasReportedScore(matchWithId(7L))).isTrue();
        assertThat(adapter.hasReportedScore(null)).isFalse();
    }

    @Test
    @DisplayName("closeAsWalkover: delega el cierre por W.O. a MatchService")
    void closeAsWalkover_DelegatesToMatchService() {
        Match match = matchWithId(7L);
        Player winner = Player.builder().id(2L).build();

        adapter.closeAsWalkover(match, winner);

        verify(matchService).closeMatchAsWalkover(7L, 2L);
    }

    @Test
    @DisplayName("closeAsWalkover: sin match o sin id no hace nada")
    void closeAsWalkover_NullMatch_NoInteraction() {
        adapter.closeAsWalkover(null, null);
        adapter.closeAsWalkover(new Match(), null);

        verifyNoInteractions(matchService);
    }
}

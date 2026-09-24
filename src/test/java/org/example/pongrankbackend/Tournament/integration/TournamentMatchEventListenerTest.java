package org.example.pongrankbackend.Tournament.integration;

import org.example.pongrankbackend.Match.event.MatchConfirmedEvent;
import org.example.pongrankbackend.Tournament.service.TournamentService;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TournamentMatchEventListenerTest {

    @Mock
    private TournamentService tournamentService;

    @InjectMocks
    private TournamentMatchEventListener listener;

    @Test
    @DisplayName("onMatchConfirmed: aplica el partido confirmado al torneo")
    void onMatchConfirmed_AppliesResult() {
        listener.onMatchConfirmed(new MatchConfirmedEvent(this, 7L, true));

        verify(tournamentService).applyConfirmedMatch(7L);
    }

    @Test
    @DisplayName("onMatchConfirmed: un error del torneo no se propaga a la confirmación del partido")
    void onMatchConfirmed_TournamentError_IsNotPropagated() {
        doThrow(new ConflictException("Resultado inválido")).when(tournamentService).applyConfirmedMatch(7L);

        assertThatCode(() -> listener.onMatchConfirmed(new MatchConfirmedEvent(this, 7L, true)))
                .doesNotThrowAnyException();
    }
}

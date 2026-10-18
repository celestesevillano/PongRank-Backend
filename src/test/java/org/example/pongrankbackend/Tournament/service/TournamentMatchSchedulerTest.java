package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Tournament.Tournament;
import org.example.pongrankbackend.Tournament.TournamentMatch;
import org.example.pongrankbackend.Tournament.TournamentMatchStatus;
import org.example.pongrankbackend.Tournament.integration.MatchIntegrationPort;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentMatchSchedulerTest {

    @Mock
    private MatchIntegrationPort matchIntegrationPort;

    @InjectMocks
    private TournamentMatchScheduler scheduler;

    private Player player(Long id) {
        return Player.builder().id(id).name("Jugador " + id).build();
    }

    private Tournament tournament() {
        return Tournament.builder().id(1L).matchFormat(MatchFormat.BO5).build();
    }

    @Test
    @DisplayName("schedule: crea el Match real con el formato del torneo y deja el partido SCHEDULED")
    void schedule_CreatesMatchAndMarksScheduled() {
        Player p1 = player(1L);
        Player p2 = player(2L);
        Match created = Match.builder().id(50L).build();
        when(matchIntegrationPort.createMatch(p1, p2, MatchFormat.BO5)).thenReturn(created);
        TournamentMatch match = TournamentMatch.builder().player1(p1).player2(p2).build();

        scheduler.schedule(tournament(), match);

        assertThat(match.getMatch()).isSameAs(created);
        assertThat(match.getStatus()).isEqualTo(TournamentMatchStatus.SCHEDULED);
    }

    @Test
    @DisplayName("schedule: no crea un segundo Match real si el partido del torneo ya tiene uno")
    void schedule_ExistingMatch_DoesNotCreateAnother() {
        Match existing = Match.builder().id(50L).build();
        TournamentMatch match = TournamentMatch.builder().player1(player(1L)).player2(player(2L)).match(existing).build();

        scheduler.schedule(tournament(), match);

        verify(matchIntegrationPort, never()).createMatch(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(match.getMatch()).isSameAs(existing);
    }

    @Test
    @DisplayName("schedule: un jugador no puede enfrentarse a si mismo")
    void schedule_SamePlayer_ThrowsConflict() {
        Player p1 = player(1L);
        TournamentMatch match = TournamentMatch.builder().player1(p1).player2(p1).build();

        assertThatThrownBy(() -> scheduler.schedule(tournament(), match))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(matchIntegrationPort);
    }

    @Test
    @DisplayName("placeInNextMatch: la posicion par ocupa player1 y la impar player2 del siguiente cruce")
    void placeInNextMatch_UsesBracketPositionParity() {
        Player winner = player(7L);
        TournamentMatch next = TournamentMatch.builder().build();

        scheduler.placeInNextMatch(TournamentMatch.builder().bracketPosition(2).winner(winner).build(), next);
        assertThat(next.getPlayer1()).isSameAs(winner);
        assertThat(next.getPlayer2()).isNull();

        scheduler.placeInNextMatch(TournamentMatch.builder().bracketPosition(3).winner(winner).build(), next);
        assertThat(next.getPlayer2()).isSameAs(winner);
    }
}

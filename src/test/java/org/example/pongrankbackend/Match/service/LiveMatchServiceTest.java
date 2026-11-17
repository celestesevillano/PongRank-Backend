package org.example.pongrankbackend.Match.service;

import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.dto.LiveMatchAction;
import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.common.exception.ConflictException;
import org.example.pongrankbackend.common.exception.InvalidMatchStateException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.example.pongrankbackend.Match.dto.LiveMatchAction.Kind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveMatchServiceTest {
    private final Match match = Match.builder().id(10L).version(0L).format(MatchFormat.BO3)
            .player1(Player.builder().id(1L).build()).player2(Player.builder().id(2L).build()).build();
    private final MatchRepository repository = mock(MatchRepository.class);
    private final LiveMatchService service = new LiveMatchService(repository);

    LiveMatchServiceTest() {
        when(repository.findById(10L)).thenReturn(Optional.of(match));
        when(repository.findForLiveUpdate(10L)).thenReturn(Optional.of(match));
        when(repository.saveAndFlush(match)).thenAnswer(call -> {
            match.setVersion(match.getVersion() + 1);
            return match;
        });
    }

    @Test
    void onlyTheParticipantsCanReadOrMutate() {
        assertThrows(UnauthorizedActionException.class, () -> service.get(10L, 3L));
        assertThrows(UnauthorizedActionException.class, () -> service.apply(10L, 3L, new LiveMatchAction(READY, null)));
        verify(repository, never()).saveAndFlush(match);
    }

    @Test
    void readinessAndPointsAreSharedAndStaleActionsDoNotChangeTheScore() {
        var ready = service.apply(10L, 1L, new LiveMatchAction(READY, null));
        assertTrue(ready.player1Ready());
        assertFalse(ready.player2Ready());
        assertThrows(InvalidMatchStateException.class, () -> service.apply(10L, 1L, new LiveMatchAction(POINT, ready.revision())));
        var started = service.apply(10L, 2L, new LiveMatchAction(READY, null));
        assertTrue(started.player1Ready() && started.player2Ready());
        var point = service.apply(10L, 2L, new LiveMatchAction(POINT, started.revision()));
        assertEquals(1, point.scorePlayer2());
        assertEquals(0, point.scorePlayer1());
        assertEquals(point, service.get(10L, 1L));
        assertThrows(ConflictException.class, () -> service.apply(10L, 1L, new LiveMatchAction(POINT, started.revision())));
        assertThrows(InvalidMatchStateException.class, () -> service.apply(10L, 1L, new LiveMatchAction(UNDO, point.revision())));
        assertEquals(0, service.apply(10L, 2L, new LiveMatchAction(UNDO, point.revision())).scorePlayer2());
    }

    @Test
    void aDeuceSetNeedsTwoPointsAndTheMatchStopsAtTwoSets() {
        String tied = "12".repeat(10);
        var ongoing = LiveScore.from(tied + "1", MatchFormat.BO3);
        assertTrue(ongoing.sets().isEmpty());
        var next = LiveScore.from(tied + "11", MatchFormat.BO3);
        assertEquals(1, next.sets().size());
        assertEquals(12, next.sets().getFirst().getScorePlayer1());
        assertEquals(10, next.sets().getFirst().getScorePlayer2());
        var finished = LiveScore.from(tied + "11" + "1".repeat(11), MatchFormat.BO3);
        assertTrue(finished.finished());
        assertEquals(2, finished.sets().size());
    }
}

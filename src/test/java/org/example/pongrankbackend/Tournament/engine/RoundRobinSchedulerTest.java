package org.example.pongrankbackend.Tournament.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoundRobinSchedulerTest {

    private final RoundRobinScheduler scheduler = new RoundRobinScheduler();

    @Test
    @DisplayName("schedule: en un grupo de 4 cada jugador enfrenta una vez a cada rival (6 partidos)")
    void schedule_GroupOfFour_EveryPairOnce() {
        List<RoundRobinScheduler.Pairing> pairings = scheduler.schedule(List.of(1L, 2L, 3L, 4L));

        Set<Set<Long>> pairs = new HashSet<>();
        pairings.forEach(p -> pairs.add(Set.of(p.player1Id(), p.player2Id())));

        assertThat(pairings).hasSize(6);
        assertThat(pairs).hasSize(6);
        for (long player = 1; player <= 4; player++) {
            long id = player;
            assertThat(pairings.stream().filter(p -> p.player1Id() == id || p.player2Id() == id)).hasSize(3);
        }
    }

    @Test
    @DisplayName("schedule: en un grupo de 3 se juegan 3 partidos")
    void schedule_GroupOfThree() {
        assertThat(scheduler.schedule(List.of(1L, 2L, 3L))).hasSize(3);
    }
}

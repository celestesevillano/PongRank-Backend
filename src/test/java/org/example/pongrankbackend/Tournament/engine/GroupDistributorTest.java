package org.example.pongrankbackend.Tournament.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupDistributorTest {

    private final GroupDistributor distributor = new GroupDistributor();

    private List<Long> seeds(int n) {
        return LongStream.rangeClosed(1, n).boxed().toList();
    }

    @Test
    @DisplayName("canDistribute: solo acepta cantidades que permiten grupos de 3 o 4")
    void canDistribute_ValidAndInvalidSizes() {
        assertThat(distributor.canDistribute(2)).isFalse();
        assertThat(distributor.canDistribute(3)).isTrue();
        assertThat(distributor.canDistribute(4)).isTrue();
        assertThat(distributor.canDistribute(5)).isFalse();
        for (int n = 6; n <= 40; n++) {
            assertThat(distributor.canDistribute(n)).as("n=%d", n).isTrue();
        }
    }

    @Test
    @DisplayName("distribute: 8 jugadores forman 2 grupos de 4 por serpentina")
    void distribute_EightPlayers_SnakeOrder() {
        List<List<Long>> groups = distributor.distribute(seeds(8));

        assertThat(groups).containsExactly(List.of(1L, 4L, 5L, 8L), List.of(2L, 3L, 6L, 7L));
    }

    @Test
    @DisplayName("distribute: 7 jugadores forman un grupo de 3 y otro de 4")
    void distribute_SevenPlayers_OneGroupOfThree() {
        List<List<Long>> groups = distributor.distribute(seeds(7));

        assertThat(groups).containsExactly(List.of(1L, 4L, 5L), List.of(2L, 3L, 6L, 7L));
    }

    @Test
    @DisplayName("distribute: siempre grupos de 3 o 4, priorizando 4, y cada jugador en un solo grupo")
    void distribute_AllSizes_GroupsOfThreeOrFour() {
        for (int n = 3; n <= 40; n++) {
            if (!distributor.canDistribute(n)) {
                continue;
            }
            List<List<Long>> groups = distributor.distribute(seeds(n));
            Set<Long> seen = new HashSet<>();
            groups.forEach(seen::addAll);

            assertThat(groups).allSatisfy(g -> assertThat(g.size()).isBetween(3, 4));
            assertThat(groups).hasSize((n + 3) / 4);
            assertThat(seen).hasSize(n);
        }
    }

    @Test
    @DisplayName("distribute: 5 jugadores no se pueden repartir")
    void distribute_FivePlayers_Throws() {
        assertThatThrownBy(() -> distributor.distribute(seeds(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

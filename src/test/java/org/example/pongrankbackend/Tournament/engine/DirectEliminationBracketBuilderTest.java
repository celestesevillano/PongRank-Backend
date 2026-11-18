package org.example.pongrankbackend.Tournament.engine;

import org.example.pongrankbackend.Tournament.engine.DirectEliminationBracketBuilder.Pairing;
import org.example.pongrankbackend.Tournament.engine.DirectEliminationBracketBuilder.Plan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectEliminationBracketBuilderTest {

    private final DirectEliminationBracketBuilder builder = new DirectEliminationBracketBuilder();

    @Test
    @DisplayName("8 jugadores: cuartos, semifinales y final sin ronda preliminar")
    void eightPlayers() {
        Plan plan = builder.build(8);

        assertThat(plan.preliminaryCount()).isZero();
        assertThat(plan.firstMainRound()).isEqualTo(1);
        assertThat(plan.pairings()).hasSize(7);
        List<Pairing> quarters = plan.pairings().stream().filter(p -> p.round() == 1).toList();
        assertThat(quarters).extracting(Pairing::seed1).containsExactly(1, 4, 2, 3);
        assertThat(quarters).extracting(Pairing::seed2).containsExactly(8, 5, 7, 6);
    }

    @Test
    @DisplayName("12 jugadores: 4 preliminares y la llave principal espera a sus ganadores")
    void twelvePlayers() {
        Plan plan = builder.build(12);

        assertThat(plan.principal()).isEqualTo(8);
        assertThat(plan.preliminaryCount()).isEqualTo(4);
        assertThat(plan.directCount()).isEqualTo(4);
        assertThat(plan.firstMainRound()).isEqualTo(2);
        List<Pairing> preliminary = plan.pairings().stream().filter(p -> p.round() == 1).toList();
        assertThat(preliminary).extracting(Pairing::seed1).containsExactlyInAnyOrder(5, 6, 7, 8);
        assertThat(preliminary).extracting(Pairing::seed2).containsExactlyInAnyOrder(12, 11, 10, 9);
        // Each preliminary match feeds a distinct slot of the first main round
        assertThat(preliminary).extracting(Pairing::position).doesNotHaveDuplicates().allMatch(slot -> slot >= 0 && slot < 8);
        // Direct seeds appear in the first main round; the other slots stay empty
        long direct = plan.pairings().stream().filter(p -> p.round() == 2)
                .flatMap(p -> java.util.stream.Stream.of(p.seed1(), p.seed2())).filter(java.util.Objects::nonNull).count();
        assertThat(direct).isEqualTo(4);
    }

    @Test
    @DisplayName("Rechaza cantidades que no son múltiplo de 4 o fuera de rango")
    void invalidCounts() {
        assertThat(builder.validate(3)).isNotNull();
        assertThat(builder.validate(6)).isNotNull();
        assertThat(builder.validate(68)).isNotNull();
        assertThat(builder.validate(8)).isNull();
        assertThatThrownBy(() -> builder.build(6)).isInstanceOf(IllegalArgumentException.class);
    }
}

package org.example.pongrankbackend.Tournament.engine;

import org.example.pongrankbackend.Tournament.engine.KnockoutBracketBuilder.BracketPlan;
import org.example.pongrankbackend.Tournament.engine.KnockoutBracketBuilder.FirstRoundPairing;
import org.example.pongrankbackend.Tournament.engine.KnockoutBracketBuilder.Qualifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KnockoutBracketBuilderTest {

    private final KnockoutBracketBuilder builder = new KnockoutBracketBuilder();

    // Group g has winner id g*10+1 and runner-up id g*10+2; seeds follow the snake so winners are better seeded
    private List<Qualifier> winners(int groups) {
        List<Qualifier> list = new ArrayList<>();
        for (int g = 1; g <= groups; g++) {
            list.add(new Qualifier(g * 10L + 1, g, g));
        }
        return list;
    }

    private List<Qualifier> runnersUp(int groups) {
        List<Qualifier> list = new ArrayList<>();
        for (int g = 1; g <= groups; g++) {
            list.add(new Qualifier(g * 10L + 2, g, 2 * groups + 1 - g));
        }
        return list;
    }

    private Set<Long> players(BracketPlan plan) {
        Set<Long> ids = new HashSet<>();
        for (FirstRoundPairing p : plan.firstRound()) {
            ids.add(p.player1().playerId());
            if (!p.isBye()) {
                ids.add(p.player2().playerId());
            }
        }
        return ids;
    }

    @Test
    @DisplayName("standardOrder: orden clásico de una llave de 8")
    void standardOrder_Eight() {
        assertThat(KnockoutBracketBuilder.standardOrder(8)).containsExactly(1, 8, 4, 5, 2, 7, 3, 6);
    }

    @Test
    @DisplayName("2 grupos: los 1.º enfrentan al 2.º del otro grupo y no hay cruces del mismo grupo")
    void twoGroups_WinnersFaceOtherGroupRunnerUp() {
        BracketPlan plan = builder.build(winners(2), runnersUp(2));

        assertThat(plan.bracketSize()).isEqualTo(4);
        assertThat(plan.sameGroupConflicts()).isEmpty();
        assertThat(plan.firstRound()).allSatisfy(p -> {
            assertThat(p.isBye()).isFalse();
            assertThat(p.player1().groupNumber()).isNotEqualTo(p.player2().groupNumber());
        });
        assertThat(players(plan)).hasSize(4);
    }

    @Test
    @DisplayName("3 grupos: 6 clasificados en llave de 8, los 2 BYE van a los mejores sembrados")
    void threeGroups_ByesToBestSeeded() {
        BracketPlan plan = builder.build(winners(3), runnersUp(3));

        List<FirstRoundPairing> byes = plan.firstRound().stream().filter(FirstRoundPairing::isBye).toList();
        assertThat(plan.bracketSize()).isEqualTo(8);
        assertThat(plan.rounds()).isEqualTo(3);
        assertThat(byes).extracting(p -> p.player1().initialSeed()).containsExactlyInAnyOrder(1, 2);
        assertThat(plan.sameGroupConflicts()).isEmpty();
        assertThat(players(plan)).hasSize(6);
    }

    @Test
    @DisplayName("5 grupos: 10 clasificados en llave de 16 con 6 BYE (los 5 primeros y el mejor segundo)")
    void fiveGroups_SixByes() {
        BracketPlan plan = builder.build(winners(5), runnersUp(5));

        List<FirstRoundPairing> byes = plan.firstRound().stream().filter(FirstRoundPairing::isBye).toList();
        assertThat(plan.bracketSize()).isEqualTo(16);
        assertThat(byes).hasSize(6);
        assertThat(byes.stream().filter(p -> p.player1().playerId() % 10 == 1)).hasSize(5);
        assertThat(plan.sameGroupConflicts()).isEmpty();
        assertThat(players(plan)).hasSize(10);
    }

    @Test
    @DisplayName("1 grupo: el cruce del mismo grupo es inevitable y se informa como conflicto")
    void oneGroup_ConflictReported() {
        BracketPlan plan = builder.build(winners(1), runnersUp(1));

        assertThat(plan.bracketSize()).isEqualTo(2);
        assertThat(plan.firstRound()).hasSize(1);
        assertThat(plan.sameGroupConflicts()).hasSize(1);
    }

    @Test
    @DisplayName("4 grupos: ninguna pareja de primera ronda es del mismo grupo")
    void fourGroups_NoSameGroupPairs() {
        BracketPlan plan = builder.build(winners(4), runnersUp(4));

        assertThat(plan.bracketSize()).isEqualTo(8);
        assertThat(plan.firstRound()).noneMatch(FirstRoundPairing::isSameGroup);
        assertThat(players(plan)).hasSize(8);
    }
}

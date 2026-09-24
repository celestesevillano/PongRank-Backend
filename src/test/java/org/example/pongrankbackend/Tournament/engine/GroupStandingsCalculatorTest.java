package org.example.pongrankbackend.Tournament.engine;

import org.example.pongrankbackend.Tournament.engine.GroupStandingsCalculator.GroupMatchResult;
import org.example.pongrankbackend.Tournament.engine.GroupStandingsCalculator.GroupStandings;
import org.example.pongrankbackend.Tournament.engine.GroupStandingsCalculator.StandingRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GroupStandingsCalculatorTest {

    private static final Long A = 1L;
    private static final Long B = 2L;
    private static final Long C = 3L;
    private static final Long D = 4L;
    private static final List<Long> GROUP = List.of(A, B, C, D);

    private final GroupStandingsCalculator calculator = new GroupStandingsCalculator();

    // winner is player1 when setsP1 > setsP2
    private GroupMatchResult played(Long p1, Long p2, int setsP1, int setsP2, int pointsP1, int pointsP2) {
        return new GroupMatchResult(p1, p2, setsP1 > setsP2 ? p1 : p2, false, setsP1, setsP2, pointsP1, pointsP2);
    }

    private GroupMatchResult walkover(Long p1, Long p2, Long winner) {
        return new GroupMatchResult(p1, p2, winner, true, 0, 0, 0, 0);
    }

    private List<Long> order(GroupStandings standings) {
        return standings.rows().stream().map(StandingRow::playerId).toList();
    }

    @Test
    @DisplayName("Sin empates: ordena por victorias")
    void noTies_OrderedByWins() {
        GroupStandings standings = calculator.calculate(GROUP, List.of(
                played(A, B, 3, 0, 33, 10), played(A, C, 3, 0, 33, 10), played(A, D, 3, 0, 33, 10),
                played(B, C, 3, 0, 33, 10), played(B, D, 3, 0, 33, 10), played(C, D, 3, 0, 33, 10)), Map.of());

        assertThat(order(standings)).containsExactly(A, B, C, D);
        assertThat(standings.unresolvedTies()).isEmpty();
    }

    @Test
    @DisplayName("Empate de dos: decide el enfrentamiento directo aunque el otro tenga mejor diferencia de sets")
    void twoWayTie_HeadToHeadWins() {
        GroupStandings standings = calculator.calculate(GROUP, List.of(
                played(A, B, 3, 2, 50, 48), played(C, A, 3, 0, 33, 10), played(A, D, 3, 2, 50, 48),
                played(B, C, 3, 0, 33, 10), played(B, D, 3, 0, 33, 10), played(D, C, 3, 1, 40, 30)), Map.of());

        assertThat(order(standings)).containsExactly(A, B, D, C);
    }

    @Test
    @DisplayName("Empate triple circular: se resuelve con la diferencia de sets de la mini-liga, no la global")
    void threeWayTie_UsesMiniLeagueSetDifference() {
        GroupStandings standings = calculator.calculate(GROUP, List.of(
                played(A, B, 3, 0, 33, 10), played(B, C, 3, 0, 33, 10), played(C, A, 3, 1, 40, 35),
                played(A, D, 3, 2, 50, 48), played(B, D, 3, 0, 33, 10), played(C, D, 3, 0, 33, 10)), Map.of());

        // Global set difference would put B first; the mini-league among A, B, C puts A first
        assertThat(order(standings)).containsExactly(A, B, C, D);
        assertThat(standings.unresolvedTies()).isEmpty();
    }

    @Test
    @DisplayName("Empate triple: si un criterio separa a uno, los dos restantes reinician con el enfrentamiento directo")
    void threeWayTie_RestartsWithHeadToHeadForRemainingPair() {
        GroupStandings standings = calculator.calculate(GROUP, List.of(
                played(A, B, 3, 0, 33, 10), played(B, C, 3, 1, 40, 35), played(C, A, 3, 2, 50, 48),
                played(A, D, 3, 0, 33, 10), played(B, D, 3, 0, 33, 10), played(C, D, 3, 0, 33, 10)), Map.of());

        // Mini-league sets: A +2, B -1, C -1 -> A first; B and C restart: B beat C
        assertThat(order(standings)).containsExactly(A, B, C, D);
    }

    @Test
    @DisplayName("Empate que ninguna regla resuelve: se informa y bloquea la clasificación")
    void unresolvableTie_IsReported() {
        GroupStandings standings = calculator.calculate(GROUP, List.of(
                played(A, B, 3, 0, 33, 15), played(B, C, 3, 0, 33, 15), played(C, A, 3, 0, 33, 15),
                played(A, D, 3, 0, 33, 10), played(B, D, 3, 0, 33, 10), played(C, D, 3, 0, 33, 10)), Map.of());

        assertThat(standings.unresolvedTies()).containsExactly(List.of(A, B, C));
        assertThat(standings.hasBlockingTie(2)).isTrue();
        assertThat(standings.rows().get(3).playerId()).isEqualTo(D);
        assertThat(standings.rows().get(3).unresolvedTie()).isFalse();
    }

    @Test
    @DisplayName("El orden manual del administrador resuelve el empate sin sorteo automático")
    void unresolvableTie_ManualOrderApplied() {
        GroupStandings standings = calculator.calculate(GROUP, List.of(
                played(A, B, 3, 0, 33, 15), played(B, C, 3, 0, 33, 15), played(C, A, 3, 0, 33, 15),
                played(A, D, 3, 0, 33, 10), played(B, D, 3, 0, 33, 10), played(C, D, 3, 0, 33, 10)),
                Map.of(C, 1, A, 2, B, 3));

        assertThat(order(standings)).containsExactly(C, A, B, D);
        assertThat(standings.unresolvedTies()).isEmpty();
        assertThat(standings.hasBlockingTie(2)).isFalse();
    }

    @Test
    @DisplayName("W.O.: cuenta como victoria pero no suma sets ni puntos")
    void walkover_CountsAsWinWithoutSetsOrPoints() {
        GroupStandings standings = calculator.calculate(List.of(A, B, C), List.of(
                walkover(A, B, A), played(A, C, 3, 1, 40, 30), played(B, C, 3, 0, 33, 10)), Map.of());

        StandingRow rowA = standings.rows().get(0);
        assertThat(rowA.playerId()).isEqualTo(A);
        assertThat(rowA.wins()).isEqualTo(2);
        assertThat(rowA.setsWon()).isEqualTo(3);
        assertThat(rowA.setsLost()).isEqualTo(1);
        assertThat(rowA.pointsWon()).isEqualTo(40);
        assertThat(order(standings)).containsExactly(A, B, C);
    }
}

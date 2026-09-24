package org.example.pongrankbackend.Tournament.engine;

import org.springframework.stereotype.Component;

import java.util.*;

/*
 * Builds the first knockout round from the group qualifiers (rules agreed with the team):
 *  - Group winners and runners-up are ranked separately, each list ordered by initial seed.
 *  - Bracket ranks: winners first (1..G), then runners-up (G+1..2G). BYEs fill the missing ranks,
 *    so the best-seeded qualifiers receive them.
 *  - Ranks are placed with the standard bracket order (1 vs N, 2 vs N-1, ...; 1 and 2 in opposite halves).
 *  - Players from the same group must not meet in round 1 when mathematically possible:
 *    the winners and the BYE receivers stay fixed; the runners-up that play round 1 are reassigned
 *    (closest to seed order first) until no same-group pairing remains.
 *  - If no valid arrangement exists, the default arrangement is returned with its conflicts,
 *    so the service can inform the admin before generating the bracket.
 */
@Component
public class KnockoutBracketBuilder {

    public record Qualifier(Long playerId, int groupNumber, int initialSeed) {
    }

    // player2 == null means player1 receives a BYE
    public record FirstRoundPairing(Qualifier player1, Qualifier player2) {

        public boolean isBye() {
            return player2 == null;
        }

        public boolean isSameGroup() {
            return player2 != null && player1.groupNumber() == player2.groupNumber();
        }
    }

    public record BracketPlan(int bracketSize, List<FirstRoundPairing> firstRound, List<FirstRoundPairing> sameGroupConflicts) {

        public int rounds() {
            return Integer.numberOfTrailingZeros(bracketSize);
        }
    }

    public BracketPlan build(List<Qualifier> groupWinners, List<Qualifier> runnersUp) {
        List<Qualifier> ranked = new ArrayList<>(sortBySeed(groupWinners));
        ranked.addAll(sortBySeed(runnersUp));

        int qualifiers = ranked.size();
        if (qualifiers < 2) {
            throw new IllegalArgumentException("Se necesitan al menos 2 clasificados para la llave");
        }
        int bracketSize = Integer.highestOneBit(qualifiers - 1) << 1;
        int[] order = standardOrder(bracketSize);

        // slot index -> rank (1-based); ranks above the number of qualifiers are BYEs
        Qualifier[] slots = new Qualifier[bracketSize];
        List<Integer> movableSlots = new ArrayList<>();
        List<Qualifier> movable = new ArrayList<>();
        int byes = bracketSize - qualifiers;

        for (int slot = 0; slot < bracketSize; slot++) {
            int rank = order[slot];
            if (rank > qualifiers) {
                continue;
            }
            Qualifier qualifier = ranked.get(rank - 1);
            slots[slot] = qualifier;

            boolean isRunnerUp = rank > groupWinners.size();
            boolean receivesBye = rank <= byes;
            if (isRunnerUp && !receivesBye) {
                movableSlots.add(slot);
                movable.add(qualifier);
            }
        }

        Qualifier[] best = slots.clone();
        if (assign(slots, movableSlots, movable, new boolean[movable.size()], 0)) {
            best = slots;
        }

        List<FirstRoundPairing> firstRound = new ArrayList<>();
        for (int slot = 0; slot < bracketSize; slot += 2) {
            Qualifier a = best[slot];
            Qualifier b = best[slot + 1];
            firstRound.add(a != null ? new FirstRoundPairing(a, b) : new FirstRoundPairing(b, null));
        }
        List<FirstRoundPairing> conflicts = firstRound.stream().filter(FirstRoundPairing::isSameGroup).toList();
        return new BracketPlan(bracketSize, firstRound, conflicts);
    }

    // Backtracking: fills movable slots in bracket order, trying runners-up in seed order first
    private boolean assign(Qualifier[] slots, List<Integer> movableSlots, List<Qualifier> movable, boolean[] used, int index) {
        if (index == movableSlots.size()) {
            return true;
        }
        int slot = movableSlots.get(index);
        int opponentSlot = slot % 2 == 0 ? slot + 1 : slot - 1;
        List<Qualifier> candidates = sortBySeed(movable);

        for (Qualifier candidate : candidates) {
            int candidateIndex = movable.indexOf(candidate);
            if (used[candidateIndex]) {
                continue;
            }
            Qualifier opponent = slots[opponentSlot];
            boolean opponentFixed = !movableSlots.contains(opponentSlot) || movableSlots.indexOf(opponentSlot) < index;
            if (opponent != null && opponentFixed && opponent.groupNumber() == candidate.groupNumber()) {
                continue;
            }
            used[candidateIndex] = true;
            slots[slot] = candidate;
            if (assign(slots, movableSlots, movable, used, index + 1)) {
                return true;
            }
            used[candidateIndex] = false;
        }
        return false;
    }

    // Standard seeded bracket order, e.g. size 8 -> [1, 8, 4, 5, 2, 7, 3, 6]
    static int[] standardOrder(int size) {
        List<Integer> order = new ArrayList<>(List.of(1, 2));
        while (order.size() < size) {
            int next = order.size() * 2;
            List<Integer> expanded = new ArrayList<>();
            for (int rank : order) {
                expanded.add(rank);
                expanded.add(next + 1 - rank);
            }
            order = expanded;
        }
        return order.stream().mapToInt(Integer::intValue).toArray();
    }

    private List<Qualifier> sortBySeed(List<Qualifier> qualifiers) {
        return qualifiers.stream().sorted(Comparator.comparingInt(Qualifier::initialSeed)).toList();
    }
}

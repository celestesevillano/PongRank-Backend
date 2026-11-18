package org.example.pongrankbackend.Tournament.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/*
 * Single-elimination bracket for N seeded players (N >= 4, multiple of 4). Mirrors the bracket drawn by the mobile app:
 *  - principal = largest power of two <= N. The excess players (N - principal) play a preliminary round (round 1):
 *    seed (direct + i + 1) vs seed (N - i), where direct = principal - preliminary.
 *  - The main rounds follow the standard seeded order; seeds <= direct enter directly, the rest wait for the
 *    winner of their preliminary match.
 *  - With no preliminary round (N is a power of two) round 1 is the first main round.
 * A preliminary match stores in bracketPosition the slot index of the main round it feeds, so the winner moves with the
 * same rule as the rest of the bracket (next position = bracketPosition / 2, even slot = player1, odd slot = player2).
 */
@Component
public class DirectEliminationBracketBuilder {

    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 64;
    public static final int MULTIPLE = 4;

    // Seeds are 1-based; a null seed means the slot is waiting for a preliminary winner
    public record Pairing(int round, int position, Integer seed1, Integer seed2) {
    }

    public record Plan(int principal, int directCount, int preliminaryCount, List<Pairing> pairings) {

        public int firstMainRound() {
            return preliminaryCount > 0 ? 2 : 1;
        }
    }

    public String validate(int count) {
        if (count < MIN_PLAYERS) {
            return "Necesitas al menos 4 participantes";
        }
        if (count % MULTIPLE != 0) {
            return "El total de participantes debe ser múltiplo de 4";
        }
        if (count > MAX_PLAYERS) {
            return "Un torneo admite como máximo " + MAX_PLAYERS + " participantes";
        }
        return null;
    }

    public Plan build(int count) {
        String error = validate(count);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        int principal = Integer.highestOneBit(count);
        int preliminaryCount = count - principal;
        int directCount = principal - preliminaryCount;
        int[] order = seedOrder(principal);

        List<Pairing> pairings = new ArrayList<>();
        int firstMainRound = preliminaryCount > 0 ? 2 : 1;

        for (int i = 0; i < preliminaryCount; i++) {
            int seed = directCount + i + 1;
            int slot = slotOf(order, seed);
            pairings.add(new Pairing(1, slot, seed, count - i));
        }
        int round = firstMainRound;
        for (int matches = principal / 2; matches >= 1; matches /= 2, round++) {
            for (int position = 0; position < matches; position++) {
                Integer seed1 = null;
                Integer seed2 = null;
                if (round == firstMainRound) {
                    seed1 = order[position * 2] <= directCount ? order[position * 2] : null;
                    seed2 = order[position * 2 + 1] <= directCount ? order[position * 2 + 1] : null;
                }
                pairings.add(new Pairing(round, position, seed1, seed2));
            }
        }
        return new Plan(principal, directCount, preliminaryCount, pairings);
    }

    private int slotOf(int[] order, int seed) {
        for (int slot = 0; slot < order.length; slot++) {
            if (order[slot] == seed) {
                return slot;
            }
        }
        throw new IllegalStateException("Semilla fuera de la llave: " + seed);
    }

    // Same order the mobile app draws: size 16 uses the fixed order below, others the standard doubling
    static int[] seedOrder(int size) {
        if (size == 16) {
            return new int[]{1, 16, 8, 9, 5, 12, 4, 13, 3, 14, 6, 11, 7, 10, 2, 15};
        }
        List<Integer> order = new ArrayList<>(List.of(1, 2));
        while (order.size() < size) {
            int next = order.size() * 2 + 1;
            List<Integer> expanded = new ArrayList<>();
            for (int seed : order) {
                expanded.add(seed);
                expanded.add(next - seed);
            }
            order = expanded;
        }
        return order.stream().mapToInt(Integer::intValue).toArray();
    }
}

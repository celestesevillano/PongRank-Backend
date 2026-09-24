package org.example.pongrankbackend.Tournament.engine;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/*
 * Group ranking rules (agreed with the team):
 *  1. Match wins (a W.O. counts as a win for the player who showed up).
 *  2. Players tied on wins are ordered with a mini-league that only uses the matches between them:
 *     a) wins among them (head-to-head), b) set difference among them, c) point difference among them.
 *     Whenever a criterion splits the tie, the sub-groups that are still tied restart from a).
 *  3. W.O. matches give no sets or points: differences only use matches really played.
 *  4. If a tie survives all criteria it is reported as unresolved; the tournament admin must order it manually.
 */
@Component
public class GroupStandingsCalculator {

    public record GroupMatchResult(Long player1Id, Long player2Id, Long winnerId, boolean walkover,
                                   int setsPlayer1, int setsPlayer2, int pointsPlayer1, int pointsPlayer2) {
    }

    public record StandingRow(Long playerId, int position, int played, int wins, int losses,
                              int setsWon, int setsLost, int pointsWon, int pointsLost, boolean unresolvedTie) {
    }

    public record GroupStandings(List<StandingRow> rows, List<List<Long>> unresolvedTies) {

        // A tie blocks the knockout only if it decides who qualifies or who finishes first/second
        public boolean hasBlockingTie(int qualifiersPerGroup) {
            return rows.stream().anyMatch(row -> row.unresolvedTie() && row.position() <= qualifiersPerGroup);
        }
    }

    private static final class Stats {
        int played;
        int wins;
        int losses;
        int setsWon;
        int setsLost;
        int pointsWon;
        int pointsLost;

        int setDifference() {
            return setsWon - setsLost;
        }

        int pointDifference() {
            return pointsWon - pointsLost;
        }
    }

    // playerIds must be in seed order: it is the fallback order inside an unresolved tie
    public GroupStandings calculate(List<Long> playerIds, List<GroupMatchResult> results, Map<Long, Integer> manualOrder) {
        Map<Long, Stats> overall = computeStats(playerIds, results);
        List<List<Long>> unresolved = new ArrayList<>();
        List<Long> ordered = new ArrayList<>();

        for (List<Long> block : partition(playerIds, player -> overall.get(player).wins)) {
            ordered.addAll(resolveTie(block, results, manualOrder, unresolved));
        }

        Set<Long> unresolvedPlayers = new HashSet<>();
        unresolved.forEach(unresolvedPlayers::addAll);

        List<StandingRow> rows = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Long playerId = ordered.get(i);
            Stats s = overall.get(playerId);
            rows.add(new StandingRow(playerId, i + 1, s.played, s.wins, s.losses,
                    s.setsWon, s.setsLost, s.pointsWon, s.pointsLost, unresolvedPlayers.contains(playerId)));
        }
        return new GroupStandings(rows, unresolved);
    }

    private List<Long> resolveTie(List<Long> tied, List<GroupMatchResult> allResults,
                                  Map<Long, Integer> manualOrder, List<List<Long>> unresolved) {
        if (tied.size() == 1) {
            return tied;
        }

        List<GroupMatchResult> miniLeague = allResults.stream()
                .filter(r -> tied.contains(r.player1Id()) && tied.contains(r.player2Id()))
                .toList();
        Map<Long, Stats> stats = computeStats(tied, miniLeague);

        List<Function<Long, Integer>> criteria = List.of(
                player -> stats.get(player).wins,
                player -> stats.get(player).setDifference(),
                player -> stats.get(player).pointDifference());

        for (Function<Long, Integer> criterion : criteria) {
            List<List<Long>> blocks = partition(tied, criterion);
            if (blocks.size() > 1) {
                List<Long> ordered = new ArrayList<>();
                for (List<Long> block : blocks) {
                    ordered.addAll(resolveTie(block, allResults, manualOrder, unresolved));
                }
                return ordered;
            }
        }

        if (manualOrder != null && tied.stream().allMatch(manualOrder::containsKey)) {
            return tied.stream().sorted(Comparator.comparing(manualOrder::get)).toList();
        }

        unresolved.add(List.copyOf(tied));
        return tied;
    }

    // Groups players with the same value, highest value first, keeping the input order inside each block
    private List<List<Long>> partition(List<Long> players, Function<Long, Integer> value) {
        Map<Integer, List<Long>> byValue = new TreeMap<>(Comparator.reverseOrder());
        for (Long player : players) {
            byValue.computeIfAbsent(value.apply(player), key -> new ArrayList<>()).add(player);
        }
        return new ArrayList<>(byValue.values());
    }

    private Map<Long, Stats> computeStats(List<Long> players, List<GroupMatchResult> results) {
        Map<Long, Stats> stats = new HashMap<>();
        players.forEach(player -> stats.put(player, new Stats()));

        for (GroupMatchResult r : results) {
            Stats p1 = stats.get(r.player1Id());
            Stats p2 = stats.get(r.player2Id());
            if (p1 == null || p2 == null) {
                continue;
            }
            p1.played++;
            p2.played++;
            Stats winner = r.winnerId().equals(r.player1Id()) ? p1 : p2;
            Stats loser = winner == p1 ? p2 : p1;
            winner.wins++;
            loser.losses++;

            if (!r.walkover()) {
                p1.setsWon += r.setsPlayer1();
                p1.setsLost += r.setsPlayer2();
                p2.setsWon += r.setsPlayer2();
                p2.setsLost += r.setsPlayer1();
                p1.pointsWon += r.pointsPlayer1();
                p1.pointsLost += r.pointsPlayer2();
                p2.pointsWon += r.pointsPlayer2();
                p2.pointsLost += r.pointsPlayer1();
            }
        }
        return stats;
    }
}

package org.example.pongrankbackend.Tournament.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Every player of a group plays exactly once against each of the others.
@Component
public class RoundRobinScheduler {

    public record Pairing(Long player1Id, Long player2Id) {
    }

    public List<Pairing> schedule(List<Long> playerIds) {
        List<Pairing> pairings = new ArrayList<>();
        for (int i = 0; i < playerIds.size(); i++) {
            for (int j = i + 1; j < playerIds.size(); j++) {
                pairings.add(new Pairing(playerIds.get(i), playerIds.get(j)));
            }
        }
        return pairings;
    }
}

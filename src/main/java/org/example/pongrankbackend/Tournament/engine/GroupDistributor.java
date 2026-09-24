package org.example.pongrankbackend.Tournament.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Splits seeded participants into groups of 4 (preferred) or 3 using the snake (serpentine) method.
@Component
public class GroupDistributor {

    public static final int PREFERRED_GROUP_SIZE = 4;
    public static final int MIN_GROUP_SIZE = 3;

    public int groupCount(int participants) {
        return (participants + PREFERRED_GROUP_SIZE - 1) / PREFERRED_GROUP_SIZE;
    }

    // Valid when every group can have 3 or 4 players (e.g. 5 participants cannot be split)
    public boolean canDistribute(int participants) {
        return participants >= MIN_GROUP_SIZE && participants >= groupCount(participants) * MIN_GROUP_SIZE;
    }

    // seededPlayerIds[0] is seed 1. Row 0 goes group 1..G, row 1 goes G..1, and so on.
    public List<List<Long>> distribute(List<Long> seededPlayerIds) {
        int participants = seededPlayerIds.size();
        if (!canDistribute(participants)) {
            throw new IllegalArgumentException("No se pueden formar grupos de 3 o 4 con " + participants + " participantes");
        }

        int groups = groupCount(participants);
        List<List<Long>> result = new ArrayList<>();
        for (int i = 0; i < groups; i++) {
            result.add(new ArrayList<>());
        }

        for (int i = 0; i < participants; i++) {
            int row = i / groups;
            int column = i % groups;
            int groupIndex = (row % 2 == 0) ? column : groups - 1 - column;
            result.get(groupIndex).add(seededPlayerIds.get(i));
        }
        return result;
    }
}

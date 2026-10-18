package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Tournament.TournamentParticipant;

import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TournamentParticipants {

    private TournamentParticipants() {
    }

    public static Map<Long, TournamentParticipant> indexByPlayer(List<TournamentParticipant> participants) {
        return participants.stream().collect(Collectors.toMap(p -> p.getPlayer().getId(), Function.identity()));
    }
}

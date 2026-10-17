package org.example.pongrankbackend.Tournament.service;

import org.example.pongrankbackend.Tournament.TournamentParticipant;
import org.example.pongrankbackend.Tournament.engine.GroupStandingsCalculator.GroupStandings;

import java.util.*;

public record GroupData(int groupNumber, List<TournamentParticipant> participants,
                        GroupStandings standings, boolean completed) {
}

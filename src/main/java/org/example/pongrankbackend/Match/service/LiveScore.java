package org.example.pongrankbackend.Match.service;

import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.MatchSet.dto.MatchSetRequestDTO;

import java.util.ArrayList;
import java.util.List;

record LiveScore(List<MatchSetRequestDTO> sets, int player1, int player2, Integer lastPointBy, boolean finished) {
    static LiveScore from(String points, MatchFormat format) {
        int required = switch (format) { case BO3 -> 2; case BO5 -> 3; case BO7 -> 4; };
        int score1 = 0;
        int score2 = 0;
        int won1 = 0;
        int won2 = 0;
        Integer last = null;
        List<MatchSetRequestDTO> sets = new ArrayList<>();
        for (int i = 0; points != null && i < points.length(); i++) {
            last = points.charAt(i) == '1' ? 1 : 2;
            if (last == 1) score1++;
            else score2++;
            if (Math.max(score1, score2) >= 11 && Math.abs(score1 - score2) >= 2) {
                sets.add(new MatchSetRequestDTO(sets.size() + 1, score1, score2));
                if (score1 > score2) won1++;
                else won2++;
                if (won1 == required || won2 == required) {
                    return new LiveScore(List.copyOf(sets), score1, score2, last, true);
                }
                score1 = 0;
                score2 = 0;
            }
        }
        return new LiveScore(List.copyOf(sets), score1, score2, last, false);
    }
}

package org.example.pongrankbackend.Player;

public enum Gender {
    FEMALE("Damas"),
    MALE("Varones");

    private final String rankingLabel;

    Gender(String rankingLabel) {
        this.rankingLabel = rankingLabel;
    }

    public String getRankingLabel() {
        return rankingLabel;
    }
}

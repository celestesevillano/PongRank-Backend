package org.example.pongrankbackend.Player;

public enum Gender {
    FEMALE("Damas"),
    MALE("Varones"),
    PREFER_NOT_TO_SAY("Damas o Varones");

    private final String rankingLabel;

    Gender(String rankingLabel) {
        this.rankingLabel = rankingLabel;
    }

    public String getRankingLabel() {
        return rankingLabel;
    }
}

package org.example.pongrankbackend.Player;

public enum InitialLevel {
    NEW(1000.0),
    BEGINNER(1200.0),
    INTERMEDIATE(1500.0),
    ADVANCED(1800.0),
    EXPERT(2000.0);

    private final double rating;

    InitialLevel(double rating) {
        this.rating = rating;
    }

    public double getRating() {
        return rating;
    }
}

package org.example.pongrankbackend.Player;

public enum InitialLevel {
    NEW("Nunca he jugado", 1000.0),
    BEGINNER("Estoy empezando", 1200.0),
    INTERMEDIATE("Juego de vez en cuando", 1500.0),
    ADVANCED("Juego seguido y compito", 1800.0),
    EXPERT("Nivel alto o federado", 2000.0);

    private final String label;
    private final double rating;

    InitialLevel(String label, double rating) {
        this.label = label;
        this.rating = rating;
    }

    public String getLabel() {
        return label;
    }

    public double getRating() {
        return rating;
    }
}

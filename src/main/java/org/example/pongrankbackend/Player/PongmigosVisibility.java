package org.example.pongrankbackend.Player;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PongmigosVisibility {
    EVERYONE,
    ONLY_ME;

    @JsonCreator
    public static PongmigosVisibility fromJson(String value) {
        return valueOf(value);
    }
}

package org.example.pongrankbackend.Match.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record LiveMatchAction(@NotNull Kind action, @PositiveOrZero Long revision) {
    public enum Kind { READY, POINT, UNDO }
}

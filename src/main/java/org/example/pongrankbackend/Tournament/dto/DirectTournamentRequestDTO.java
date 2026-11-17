package org.example.pongrankbackend.Tournament.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.pongrankbackend.Match.MatchFormat;

import java.time.Instant;
import java.util.List;

public record DirectTournamentRequestDTO(
        @NotNull @Positive Long clubId,
        @NotBlank @Size(max = 150) String name,
        @NotNull MatchFormat matchFormat,
        @NotNull Instant scheduledAt,
        @NotBlank @Size(max = 64) String publicationKey,
        @NotNull @Size(min = 4) List<@NotNull @Positive Long> playerIds
) {}

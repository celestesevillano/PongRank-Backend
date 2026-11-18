package org.example.pongrankbackend.Tournament.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Match.MatchFormat;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectEliminationCreateRequestDTO {

    @NotNull(message = "El ID del club organizador es obligatorio")
    private Long clubId;

    @NotBlank(message = "El nombre del torneo es obligatorio")
    @Size(max = 150, message = "El nombre del torneo no debe exceder 150 caracteres")
    private String name;

    @NotNull(message = "El formato de partido es obligatorio (BO3, BO5 o BO7)")
    private MatchFormat matchFormat;

    @NotNull(message = "La fecha y hora del torneo son obligatorias")
    private Instant scheduledAt;

    @NotEmpty(message = "Debes indicar a los participantes")
    private List<Long> playerIds;

    // Optional: repeating the same key for the same club returns the tournament already published
    @Size(max = 80, message = "La clave de publicación no debe exceder 80 caracteres")
    private String publicationKey;
}

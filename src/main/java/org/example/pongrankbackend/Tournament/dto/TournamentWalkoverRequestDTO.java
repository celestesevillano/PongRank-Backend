package org.example.pongrankbackend.Tournament.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentWalkoverRequestDTO {

    @NotNull(message = "El ID del jugador ausente es obligatorio")
    private Long absentPlayerId;
}

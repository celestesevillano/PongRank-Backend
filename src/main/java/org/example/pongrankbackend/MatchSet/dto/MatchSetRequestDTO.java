package org.example.pongrankbackend.MatchSet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchSetRequestDTO {

    @NotNull(message = "El número de set es obligatorio")
    @Min(value = 1, message = "El número de set debe ser mayor o igual a 1")
    private Integer setNumber;

    @NotNull(message = "El puntaje del jugador 1 es obligatorio")
    @Min(value = 0, message = "El puntaje del jugador 1 no puede ser negativo")
    private Integer scorePlayer1;

    @NotNull(message = "El puntaje del jugador 2 es obligatorio")
    @Min(value = 0, message = "El puntaje del jugador 2 no puede ser negativo")
    private Integer scorePlayer2;
}

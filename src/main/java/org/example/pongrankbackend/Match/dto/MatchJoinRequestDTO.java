package org.example.pongrankbackend.Match.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchJoinRequestDTO {

    @NotNull(message = "La latitud es obligatoria para unirse a un partido libre")
    private BigDecimal latitude;

    @NotNull(message = "La longitud es obligatoria para unirse a un partido libre")
    private BigDecimal longitude;
}

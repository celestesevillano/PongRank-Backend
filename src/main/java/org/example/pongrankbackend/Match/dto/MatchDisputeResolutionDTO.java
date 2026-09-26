package org.example.pongrankbackend.Match.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchDisputeResolutionDTO {

    // Si es null, el admin anula el partido (no hay ganador, no afecta el rating)
    private Long winnerId;

    @NotBlank(message = "La resolución debe explicar la decisión tomada")
    @Size(max = 500, message = "La resolución no debe exceder 500 caracteres")
    private String resolutionNote;
}

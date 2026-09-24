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
public class MatchDisputeRequestDTO {

    @NotBlank(message = "El motivo de la disputa es obligatorio")
    @Size(max = 500, message = "El motivo de la disputa no debe exceder 500 caracteres")
    private String reason;
}

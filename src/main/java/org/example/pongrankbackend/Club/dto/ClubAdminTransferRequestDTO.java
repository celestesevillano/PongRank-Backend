package org.example.pongrankbackend.Club.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClubAdminTransferRequestDTO {

    @NotNull(message = "El ID del nuevo administrador es obligatorio")
    private Long newAdminPlayerId;
}

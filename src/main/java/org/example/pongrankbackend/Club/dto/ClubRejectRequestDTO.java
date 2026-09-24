package org.example.pongrankbackend.Club.dto;

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
public class ClubRejectRequestDTO {

    @NotBlank(message = "El motivo de rechazo es obligatorio")
    @Size(max = 500, message = "El motivo de rechazo no debe exceder 500 caracteres")
    private String rejectionReason;
}

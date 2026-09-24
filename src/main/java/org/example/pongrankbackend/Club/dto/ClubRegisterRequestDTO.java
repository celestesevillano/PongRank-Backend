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
public class ClubRegisterRequestDTO {

    @NotBlank(message = "El nombre del club es obligatorio")
    @Size(max = 150, message = "El nombre del club no debe exceder 150 caracteres")
    private String name;

    @NotBlank(message = "La dirección del club es obligatoria")
    @Size(max = 250, message = "La dirección no debe exceder 250 caracteres")
    private String address;

    @NotBlank(message = "El documento de afiliación federativa es obligatorio")
    @Size(max = 500, message = "La referencia al documento de afiliación no debe exceder 500 caracteres")
    private String affiliationDocumentUrl;
}

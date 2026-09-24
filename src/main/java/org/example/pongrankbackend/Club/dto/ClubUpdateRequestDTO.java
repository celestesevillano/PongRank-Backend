package org.example.pongrankbackend.Club.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClubUpdateRequestDTO {

    @Size(max = 150, message = "El nombre del club no debe exceder 150 caracteres")
    @Pattern(regexp = ".*\\S.*", message = "El nombre del club no puede estar en blanco")
    private String name;

    @Size(max = 250, message = "La dirección no debe exceder 250 caracteres")
    @Pattern(regexp = ".*\\S.*", message = "La dirección no puede estar en blanco")
    private String address;
}

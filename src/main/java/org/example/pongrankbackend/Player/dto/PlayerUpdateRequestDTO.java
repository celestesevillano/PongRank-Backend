package org.example.pongrankbackend.Player.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import org.example.pongrankbackend.Player.Gender;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerUpdateRequestDTO {

    @Size(max = 100, message = "El nombre no debe exceder 100 caracteres")
    private String name;

    @Size(max = 30, message = "El número de WhatsApp no debe exceder 30 caracteres")
    private String whatsapp;

    private Boolean shareContact;

    @Past(message = "La fecha de nacimiento debe ser una fecha pasada")
    private LocalDate birthDate;

    private Gender gender;

    @Size(max = 50, message = "La categoría FDPTM no debe exceder 50 caracteres")
    private String categoryFdptm;

    private Boolean federatedDeclared;
}

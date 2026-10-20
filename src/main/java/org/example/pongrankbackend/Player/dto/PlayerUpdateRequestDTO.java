package org.example.pongrankbackend.Player.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Player.Gender;

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

    @Size(max = 50, message = "La categoría FDPTM no debe exceder 50 caracteres")
    private String categoryFdptm;

    private Boolean federatedDeclared;

    @Pattern(regexp = "^[a-zA-Z0-9._]{3,30}$",
            message = "El nombre de usuario debe tener entre 3 y 30 caracteres: letras, números, punto o guion bajo")
    private String username;

    @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
    private LocalDate birthDate;

    private Gender gender;
}

package org.example.pongrankbackend.Player.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class PlayerRegisterRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no debe exceder 100 caracteres")
    private String name;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    @Size(max = 150, message = "El email no debe exceder 150 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "La contraseña debe contener al menos una letra y un número")
    private String password;

    @Size(max = 30, message = "El número de WhatsApp no debe exceder 30 caracteres")
    private String whatsapp;

    @Builder.Default
    private Boolean shareContact = false;

    @Size(max = 50, message = "La categoría FDPTM no debe exceder 50 caracteres")
    private String categoryFdptm;

    @Builder.Default
    private Boolean federatedDeclared = false;
}

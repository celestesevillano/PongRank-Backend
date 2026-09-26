package org.example.pongrankbackend.Player.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteAccountRequestDTO {

    @NotBlank(message = "Debes confirmar tu contraseña para eliminar la cuenta")
    private String password;
}

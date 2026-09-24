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
public class ClubAffiliationDocumentRequestDTO {

    @NotBlank(message = "El nuevo documento de afiliación es obligatorio")
    @Size(max = 500, message = "La referencia al documento de afiliación no debe exceder 500 caracteres")
    private String affiliationDocumentUrl;
}

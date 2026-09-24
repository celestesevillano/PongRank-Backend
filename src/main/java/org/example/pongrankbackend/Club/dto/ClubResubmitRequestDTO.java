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
public class ClubResubmitRequestDTO {

    // Optional: if null, the club is resubmitted with its current document
    @Size(max = 500, message = "La referencia al documento de afiliación no debe exceder 500 caracteres")
    @Pattern(regexp = ".*\\S.*", message = "La referencia al documento de afiliación no puede estar en blanco")
    private String affiliationDocumentUrl;
}

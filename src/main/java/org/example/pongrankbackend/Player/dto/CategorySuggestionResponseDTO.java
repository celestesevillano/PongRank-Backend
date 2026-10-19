package org.example.pongrankbackend.Player.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorySuggestionResponseDTO {

    // Mismo nombre que usa el ranking nacional de la FDPTM, p. ej. "Varones 13 años"
    private String category;
    private Integer age;
    // Siempre una sugerencia a partir de la edad: la categoría real la declara el jugador
    private String note;
}

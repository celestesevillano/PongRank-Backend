package org.example.pongrankbackend.Player.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerSummaryDTO {

    private Long id;
    private String name;
    private Double ratingGlicko;
    private String categoryFdptm;
    private Boolean federatedDeclared;

    // null salvo que el jugador tenga shareContact=true (ver ModelMapperConfig)
    private String whatsapp;
}

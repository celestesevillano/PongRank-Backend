package org.example.pongrankbackend.Player.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PlayerSummaryDTO {

    private Long id;
    private String name;
    private String username;
    private Double ratingGlicko;
    private String categoryFdptm;
    private Boolean federatedDeclared;

    // Only populated for the opponent in authorized match responses.
    @Setter(AccessLevel.NONE)
    private String whatsapp;
}

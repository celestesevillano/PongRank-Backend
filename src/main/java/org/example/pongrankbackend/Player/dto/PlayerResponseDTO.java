package org.example.pongrankbackend.Player.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerResponseDTO {

    private Long id;
    private String name;
    private String email;
    private String whatsapp;
    private Boolean shareContact;
    private String categoryFdptm;
    private Boolean federatedDeclared;
    private Double ratingGlicko;
    private Double ratingDeviation;
    private Double volatility;
    private Role role;
    private PlayerStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

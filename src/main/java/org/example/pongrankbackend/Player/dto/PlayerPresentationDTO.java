package org.example.pongrankbackend.Player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlayerPresentationDTO(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 60) String city,
        @Size(max = 180) String description,
        @Size(max = 240000) String photo,
        @Size(max = 200) String instagram,
        @Size(max = 200) String tiktok,
        @Size(max = 200) String facebook
) {
}

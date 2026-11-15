package org.example.pongrankbackend.Friendship.dto;

import jakarta.validation.constraints.NotNull;
import org.example.pongrankbackend.Player.PongmigosVisibility;

public record PongmigosVisibilityDTO(
        @NotNull(message = "Indica quién puede ver tu lista de Pongmigos")
        PongmigosVisibility visibility
) {}

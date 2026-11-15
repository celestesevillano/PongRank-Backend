package org.example.pongrankbackend.Community.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CommunityTransferRequestDTO(@NotNull @Positive Long playerId) {
}

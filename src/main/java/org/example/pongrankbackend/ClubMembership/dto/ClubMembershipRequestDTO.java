package org.example.pongrankbackend.ClubMembership.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClubMembershipRequestDTO {

    @NotNull(message = "El ID del club es obligatorio")
    private Long clubId;
}

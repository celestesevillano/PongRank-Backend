package org.example.pongrankbackend.Membership.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Membership.MembershipPlan;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePreferenceRequestDTO {

    @NotNull(message = "El plan es obligatorio")
    private MembershipPlan plan;
}

package org.example.pongrankbackend.CommunityMembership.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityMemberRoleUpdateDTO {
    @NotNull(message = "El rol es obligatorio")
    private CommunityRole role;
}
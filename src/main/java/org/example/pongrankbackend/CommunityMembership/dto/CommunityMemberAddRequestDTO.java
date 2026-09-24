package org.example.pongrankbackend.CommunityMembership.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityMemberAddRequestDTO {
    private Long playerId;
}
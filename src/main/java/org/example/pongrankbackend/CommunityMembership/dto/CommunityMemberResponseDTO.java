package org.example.pongrankbackend.CommunityMembership.dto;

import lombok.*;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.CommunityMembership.MembershipStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityMemberResponseDTO {

    private Long membershipId;
    private Long playerId;
    private String playerName;
    private Double ratingGlicko;
    private CommunityRole role;
    private MembershipStatus status;
    private LocalDateTime joinedAt;
}
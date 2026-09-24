package org.example.pongrankbackend.ClubMembership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.ClubMembership.ClubMembershipRole;
import org.example.pongrankbackend.ClubMembership.ClubMembershipStatus;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClubMembershipResponseDTO {

    private Long id;
    private PlayerSummaryDTO player;
    private Long clubId;
    private String clubName;
    private ClubMembershipStatus status;
    private ClubMembershipRole role;
    private LocalDateTime createdAt;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private LocalDateTime updatedAt;
}

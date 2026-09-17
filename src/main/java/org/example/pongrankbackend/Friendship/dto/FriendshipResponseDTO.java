package org.example.pongrankbackend.Friendship.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Friendship.FriendshipStatus;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendshipResponseDTO {

    private Long id;
    private PlayerSummaryDTO playerA;
    private PlayerSummaryDTO playerB;
    private FriendshipStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

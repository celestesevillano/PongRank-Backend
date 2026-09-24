package org.example.pongrankbackend.Club.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClubResponseDTO {

    private Long id;
    private String name;
    private String address;
    private ClubStatus status;
    private PlayerSummaryDTO admin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package org.example.pongrankbackend.TrainingSession.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingSessionResponseDTO {

    private Long id;
    private PlayerSummaryDTO player;
    private Double swingVelocityMax;
    private Double postureScore;
    private Double kneeFlexionAngle;
    private Double shoulderRotationAngle;
    private Integer durationSeconds;
    private LocalDateTime createdAt;
    private boolean newPersonalBest;
}

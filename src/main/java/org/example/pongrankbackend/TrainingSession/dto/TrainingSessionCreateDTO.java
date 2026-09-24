package org.example.pongrankbackend.TrainingSession.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingSessionCreateDTO {

    @NotNull(message = "La velocidad máxima de swing es obligatoria")
    private Double swingVelocityMax;

    @NotNull(message = "El puntaje de postura es obligatorio")
    @DecimalMin(value = "0.0", message = "El puntaje de postura no puede ser negativo")
    @DecimalMax(value = "100.0", message = "El puntaje de postura no puede superar 100")
    private Double postureScore;

    private Double kneeFlexionAngle;

    private Double shoulderRotationAngle;

    @NotNull(message = "La duración de la sesión es obligatoria")
    @Min(value = 1, message = "La duración debe ser de al menos 1 segundo")
    private Integer durationSeconds;

    private String rawPoseKeypointsJson;
}

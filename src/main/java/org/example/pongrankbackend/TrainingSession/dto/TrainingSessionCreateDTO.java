package org.example.pongrankbackend.TrainingSession.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
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
    @DecimalMin(value = "0.0", message = "La velocidad máxima de swing no puede ser negativa")
    @DecimalMax(value = "50.0", message = "La velocidad máxima de swing no puede superar 50 m/s")
    private Double swingVelocityMax;

    @NotNull(message = "El puntaje de postura es obligatorio")
    @DecimalMin(value = "0.0", message = "El puntaje de postura no puede ser negativo")
    @DecimalMax(value = "100.0", message = "El puntaje de postura no puede superar 100")
    private Double postureScore;

    @DecimalMin(value = "0.0", message = "El ángulo de flexión de rodilla no puede ser negativo")
    @DecimalMax(value = "180.0", message = "El ángulo de flexión de rodilla no puede superar 180°")
    private Double kneeFlexionAngle;

    @DecimalMin(value = "-180.0", message = "El ángulo de rotación de hombro no puede ser menor a -180°")
    @DecimalMax(value = "180.0", message = "El ángulo de rotación de hombro no puede superar 180°")
    private Double shoulderRotationAngle;

    @NotNull(message = "La duración de la sesión es obligatoria")
    @Min(value = 1, message = "La duración debe ser de al menos 1 segundo")
    @Max(value = 14400, message = "La duración no puede superar 4 horas (14400 segundos)")
    private Integer durationSeconds;

    private String rawPoseKeypointsJson;
}

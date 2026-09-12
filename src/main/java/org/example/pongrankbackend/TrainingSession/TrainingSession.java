package org.example.pongrankbackend.TrainingSession;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.pongrankbackend.Player.Player;

import java.time.LocalDateTime;

@Entity
@Table(name = "training_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "swing_velocity_max")
    private Double swingVelocityMax;

    @Column(name = "posture_score")
    private Double postureScore;

    @Column(name = "knee_flexion_angle")
    private Double kneeFlexionAngle;

    @Column(name = "shoulder_rotation_angle")
    private Double shoulderRotationAngle;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "raw_pose_keypoints_json", columnDefinition = "TEXT")
    private String rawPoseKeypointsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

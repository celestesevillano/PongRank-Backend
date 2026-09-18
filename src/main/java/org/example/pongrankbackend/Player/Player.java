package org.example.pongrankbackend.Player;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.example.pongrankbackend.CommunityMembership.CommunityMembership;
import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.TrainingSession.TrainingSession;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "players",
    indexes = {
        @Index(name = "idx_player_email", columnList = "email")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank
    @Email
    @Size(max = 150)
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String password;

    @Size(max = 30)
    @Column(length = 30)
    private String whatsapp;

    @Column(name = "share_contact")
    @Builder.Default
    private Boolean shareContact = false;

    @Size(max = 50)
    @Column(name = "category_fdptm", length = 50)
    private String categoryFdptm;

    @Column(name = "federated_declared", nullable = false)
    @Builder.Default
    private Boolean federatedDeclared = false;

    @Column(name = "rating_glicko", nullable = false)
    @Builder.Default
    private Double ratingGlicko = 1500.0;

    @Column(name = "rating_deviation", nullable = false)
    @Builder.Default
    private Double ratingDeviation = 350.0;

    @Column(nullable = false)
    @Builder.Default
    private Double volatility = 0.06;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Role role = Role.ROLE_USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PlayerStatus status = PlayerStatus.ACTIVE;

    @Builder.Default
    @OneToMany(mappedBy = "player", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CommunityMembership> memberships = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "player", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrainingSession> trainingSessions = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "player1", fetch = FetchType.LAZY)
    private List<Match> matchesAsPlayer1 = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "player2", fetch = FetchType.LAZY)
    private List<Match> matchesAsPlayer2 = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "playerA", fetch = FetchType.LAZY)
    private List<Friendship> sentFriendships = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "playerB", fetch = FetchType.LAZY)
    private List<Friendship> receivedFriendships = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

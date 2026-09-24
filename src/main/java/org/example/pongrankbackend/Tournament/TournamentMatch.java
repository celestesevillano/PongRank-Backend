package org.example.pongrankbackend.Tournament;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Player.Player;

import java.time.LocalDateTime;

// Tournament-side fixture. The real game (sets and points) lives in the Match module; this row only links to it.
@Entity
@Table(name = "tournament_matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TournamentStage stage;

    @Column(name = "group_number")
    private Integer groupNumber;

    // Knockout only: round 1 is the first knockout round
    private Integer round;

    // Knockout only: position of the match inside its round (0-based)
    @Column(name = "bracket_position")
    private Integer bracketPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player1_id")
    private Player player1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player2_id")
    private Player player2;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", unique = true)
    private Match match;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TournamentMatchStatus status = TournamentMatchStatus.PENDING_PLAYERS;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private Player winner;

    // Result copied from the confirmed Match; null for W.O. and BYE (no fictitious sets or points)
    @Column(name = "sets_player1")
    private Integer setsPlayer1;

    @Column(name = "sets_player2")
    private Integer setsPlayer2;

    @Column(name = "points_player1")
    private Integer pointsPlayer1;

    @Column(name = "points_player2")
    private Integer pointsPlayer2;

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

    public boolean hasPlayer(Long playerId) {
        return (player1 != null && player1.getId().equals(playerId))
                || (player2 != null && player2.getId().equals(playerId));
    }
}

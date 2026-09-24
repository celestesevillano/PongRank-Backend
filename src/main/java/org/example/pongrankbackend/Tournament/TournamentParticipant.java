package org.example.pongrankbackend.Tournament;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.pongrankbackend.Player.Player;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "tournament_participants",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tournament_player", columnNames = {"tournament_id", "player_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @NotNull
    @Column(nullable = false)
    private Integer seed;

    @Column(name = "group_number")
    private Integer groupNumber;

    // Order set by the tournament admin only to resolve a tie the rules cannot break
    @Column(name = "tiebreak_order")
    private Integer tiebreakOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

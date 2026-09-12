package org.example.pongrankbackend.CommunityMembership;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Player.Player;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "community_memberships",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_player_community", columnNames = {"player_id", "community_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private CommunityRole role = CommunityRole.MEMBER;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        if (this.joinedAt == null) {
            this.joinedAt = LocalDateTime.now();
        }
    }
}

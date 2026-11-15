package org.example.pongrankbackend.Player;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "player_presentations")
@Getter
@Setter
@NoArgsConstructor
public class PlayerPresentation {
    @Id
    private Long playerId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id")
    private Player player;

    @Column(length = 60)
    private String city;
    @Column(length = 180)
    private String description;
    @Column(columnDefinition = "TEXT")
    private String photo;
    @Column(length = 200)
    private String instagram;
    @Column(length = 200)
    private String tiktok;
    @Column(length = 200)
    private String facebook;

    public PlayerPresentation(Player player) {
        this.player = player;
    }
}

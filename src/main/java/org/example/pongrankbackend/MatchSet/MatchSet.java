package org.example.pongrankbackend.MatchSet;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.pongrankbackend.Match.Match;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "match_sets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Match match;

    @NotNull
    @Min(1)
    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    @NotNull
    @Min(0)
    @Column(name = "score_player1", nullable = false)
    private Integer scorePlayer1;

    @NotNull
    @Min(0)
    @Column(name = "score_player2", nullable = false)
    private Integer scorePlayer2;
}

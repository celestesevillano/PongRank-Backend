package org.example.pongrankbackend.Federation;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDate;

@Entity
@Getter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"ranking_date", "category", "ranking_position"}))
public class FederatedRankingEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private String club;
    @Column(nullable = false, length = 50)
    private String category;
    @Column(name = "ranking_date", nullable = false)
    private LocalDate rankingDate;
    @Column(name = "ranking_position", nullable = false)
    private int rankingPosition;
}

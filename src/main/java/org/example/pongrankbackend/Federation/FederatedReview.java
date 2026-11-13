package org.example.pongrankbackend.Federation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class FederatedReview {
    public enum Status { PENDING, VERIFIED, REJECTED }

    @Id
    private Long playerId;
    @Version
    private Long version;
    @Column(nullable = false, length = 50)
    private String category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;
    @Column(nullable = false)
    private Instant submittedAt = Instant.now();
    private Long reviewedBy;
    private Instant reviewedAt;
    @Column(length = 500)
    private String reason;
    private Long rankingEntryId;

    FederatedReview(Long playerId, String category) {
        this.playerId = playerId;
        this.category = category;
    }
}

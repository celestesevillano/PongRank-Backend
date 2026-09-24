package org.example.pongrankbackend.Club;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Tournament.Tournament;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clubs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name;

    @NotBlank
    @Size(max = 250)
    @Column(nullable = false, length = 250)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "b2b_plan", length = 30)
    private B2BPlan b2bPlan;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ClubStatus status = ClubStatus.PENDING;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Player admin;

    @NotBlank
    @Size(max = 500)
    @Column(name = "affiliation_document_url", nullable = false, length = 500)
    private String affiliationDocumentUrl;

    @Size(max = 500)
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Builder.Default
    @OneToMany(mappedBy = "club", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Tournament> tournaments = new ArrayList<>();

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

    public boolean canOrganizeTournaments() {
        return this.status == ClubStatus.APPROVED;
    }
}

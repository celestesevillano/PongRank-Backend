package org.example.pongrankbackend.Community;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.example.pongrankbackend.CommunityMembership.CommunityMembership;
import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Tournament.Tournament;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "communities",
        indexes = {
                @Index(name = "idx_community_name", columnList = "name"),
                @Index(name = "idx_community_type_status", columnList = "community_type, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Community {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Size(max = 500)
    @Column(length = 500)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "community_type", nullable = false, length = 30)
    private CommunityType communityType;

    // CAMBIOS: Habilita el borrado lógico
    // Estado del ciclo de vida. Por defecto toda comunidad nace ACTIVE..
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CommunityStatus status = CommunityStatus.ACTIVE;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private Player creator;

    // CAMBIOS: orphanRemoval = true eliminado
    // Salir de la comunidad es un UPDATE de MembershipStatus
    @Builder.Default
    @OneToMany(mappedBy = "community", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<CommunityMembership> memberships = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "community", fetch = FetchType.LAZY)
    private List<Tournament> tournaments = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "community", fetch = FetchType.LAZY)
    private List<Match> matches = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    //Cuando cierro la comunidad
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

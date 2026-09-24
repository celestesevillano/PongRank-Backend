package org.example.pongrankbackend.Community.dto;

import lombok.*;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.Community.CommunityType;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityResponseDTO {
    private Long id;
    private String name;
    private String description;
    private CommunityType communityType;
    private CommunityStatus status;
    // Calculado por el Service con la consulta agrupada del repositorio
    // (countActiveMembersByCommunityIds), no navegando la colección.
    // Así se evita el problema N+1 al listar varias comunidades.
    private Long memberCount;
    // Rol del jugador autenticado en esta comunidad.
    // En EP3 siempre tiene valor (son sus comunidades); en EP2 puede ser null
    // si no pertenece. Permite al frontend decidir si muestra "Unirme"
    // o el panel de administración.
    private CommunityRole myRole;
    private LocalDateTime createdAt;
}

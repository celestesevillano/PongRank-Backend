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
public class CommunityDetailResponseDTO {
    private Long id;
    private String name;
    private String description;
    private CommunityType communityType;
    private CommunityStatus status;
    private Long memberCount;

    // Datos mínimos del fundador, aplanados en campos simples.
    // NO se expone el objeto Player completo: arrastraría el hash de la
    // contraseña, el email y todas sus relaciones LAZY.
    //
    // Recordatorio: el creador es un dato histórico. Los permisos NUNCA se
    // verifican contra este campo, sino contra la membresía ACTIVE con rol
    // COMMUNITY_ADMIN, porque el creador pudo haber salido.
    private Long creatorId;
    private String creatorName;

    // Contexto del jugador autenticado, para que el frontend sepa qué mostrar.
    // myRole es null cuando isMember es false.
    private Boolean isMember;
    private CommunityRole myRole;

    private LocalDateTime createdAt;
}

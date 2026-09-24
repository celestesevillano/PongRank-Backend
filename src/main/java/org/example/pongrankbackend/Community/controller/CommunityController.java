package org.example.pongrankbackend.Community.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Community.CommunityType;
import org.example.pongrankbackend.Community.dto.CommunityCreateRequestDTO;
import org.example.pongrankbackend.Community.dto.CommunityDetailResponseDTO;
import org.example.pongrankbackend.Community.dto.CommunityResponseDTO;
import org.example.pongrankbackend.Community.dto.CommunityUpdateRequestDTO;
import org.example.pongrankbackend.Community.service.CommunityService;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberAddRequestDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberResponseDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberRoleUpdateDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityRankingEntryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Endpoints REST del módulo Community.
 *
 * Versionado en /api/v1/ y recursos en plural, según las convenciones REST
 * que exige la rúbrica. Las membresías se exponen como sub-recurso
 * (/communities/{id}/members) porque no existen fuera de una comunidad.
 *
 * El controlador no contiene lógica de negocio: valida el formato de entrada
 * con @Valid, delega en el servicio y traduce el resultado a códigos HTTP.
 * Los códigos de error los produce el @ControllerAdvice global a partir de
 * las excepciones lanzadas por el servicio.
 */
@RestController
@RequestMapping("/api/v1/communities")
public class CommunityController {

    // Inyección por constructor, sin @Autowired, igual que PlayerController.
    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    // =====================================================================
    // Comunidades
    // =====================================================================

    /**
     * EP1 - Crea una comunidad. El solicitante queda como administrador.
     *
     * Responde 201 Created con el header Location apuntando al nuevo recurso,
     * como indica la semántica REST para creaciones.
     */
    @PostMapping
    public ResponseEntity<CommunityResponseDTO> createCommunity(
            @Valid @RequestBody CommunityCreateRequestDTO dto,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext when JWT is integrated

        CommunityResponseDTO response = communityService.createCommunity(dto, requesterId);

        URI location = UriComponentsBuilder.fromPath("/api/v1/communities/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /**
     * EP2 - Búsqueda paginada de comunidades activas.
     *
     * @PageableDefault fija el tamaño por defecto y el máximo lo controla
     * spring.data.web.pageable.max-page-size en application.properties,
     * evitando que alguien solicite miles de registros de una vez.
     *
     * Los filtros son opcionales: sin ellos se listan todas las activas.
     */
    @GetMapping
    public ResponseEntity<Page<CommunityResponseDTO>> searchCommunities(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) CommunityType type,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(communityService.searchCommunities(name, type, pageable, requesterId));
    }

    /**
     * EP3 - Comunidades del jugador autenticado.
     *
     * La ruta literal /me se declara antes que /{id} por legibilidad.
     * No hay ambigüedad real porque {id} es Long y "me" no puede convertirse.
     */
    @GetMapping("/me")
    public ResponseEntity<List<CommunityResponseDTO>> getMyCommunities(
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(communityService.getMyCommunities(requesterId));
    }

    /**
     * EP4 - Ficha detallada de una comunidad.
     */
    @GetMapping("/{communityId}")
    public ResponseEntity<CommunityDetailResponseDTO> getCommunityById(
            @PathVariable Long communityId,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(communityService.getCommunityById(communityId, requesterId));
    }

    /**
     * EP5 - Actualiza nombre y descripción. Solo administradores.
     *
     * PUT y no PATCH porque reemplaza por completo el conjunto de campos
     * editables del recurso.
     */
    @PutMapping("/{communityId}")
    public ResponseEntity<CommunityResponseDTO> updateCommunity(
            @PathVariable Long communityId,
            @Valid @RequestBody CommunityUpdateRequestDTO dto,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(communityService.updateCommunity(communityId, dto, requesterId));
    }

    /**
     * EP6 - Archiva la comunidad (borrado lógico). Solo administradores.
     *
     * Responde 204 No Content: la operación tuvo éxito y no hay cuerpo que
     * devolver. El verbo DELETE se conserva porque desde la perspectiva del
     * cliente el recurso deja de estar disponible, aunque internamente solo
     * cambie de estado.
     */
    @DeleteMapping("/{communityId}")
    public ResponseEntity<Void> archiveCommunity(
            @PathVariable Long communityId,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        communityService.archiveCommunity(communityId, requesterId);
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // Membresías (sub-recurso)
    // =====================================================================

    /**
     * EP7 - Lista paginada de miembros activos.
     */
    @GetMapping("/{communityId}/members")
    public ResponseEntity<Page<CommunityMemberResponseDTO>> getCommunityMembers(
            @PathVariable Long communityId,
            @PageableDefault(size = 20) Pageable pageable,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(communityService.getCommunityMembers(communityId, pageable, requesterId));
    }

    /**
     * EP8 - Ranking interno por rating Glicko-2.
     *
     * No admite parámetro de ordenación: el orden por rating es intrínseco
     * al recurso y lo fija la consulta del repositorio. Un ranking ordenado
     * por otro criterio dejaría de ser un ranking.
     */
    @GetMapping("/{communityId}/ranking")
    public ResponseEntity<Page<CommunityRankingEntryDTO>> getCommunityRanking(
            @PathVariable Long communityId,
            @PageableDefault(size = 20) Pageable pageable,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(communityService.getCommunityRanking(communityId, pageable, requesterId));
    }

    /**
     * EP9 - Ingreso a la comunidad.
     *
     * El cuerpo es opcional (required = false): sin él, el solicitante se une
     * a sí mismo; con playerId, un administrador agrega a ese jugador.
     *
     * POST porque crea un sub-recurso, y 201 Created en consecuencia.
     */
    @PostMapping("/{communityId}/members")
    public ResponseEntity<CommunityMemberResponseDTO> addMember(
            @PathVariable Long communityId,
            @RequestBody(required = false) CommunityMemberAddRequestDTO dto,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        CommunityMemberResponseDTO response = communityService.addMember(communityId, dto, requesterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * EP10 - Cambia el rol de un miembro. Solo administradores.
     *
     * PATCH y no PUT porque modifica un único atributo de la membresía,
     * no el recurso completo.
     */
    @PatchMapping("/{communityId}/members/{playerId}/role")
    public ResponseEntity<CommunityMemberResponseDTO> updateMemberRole(
            @PathVariable Long communityId,
            @PathVariable Long playerId,
            @Valid @RequestBody CommunityMemberRoleUpdateDTO dto,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        return ResponseEntity.ok(
                communityService.updateMemberRole(communityId, playerId, dto, requesterId));
    }

    /**
     * EP11 - Salida voluntaria o expulsión.
     *
     * Un mismo endpoint cubre ambos casos: el servicio distingue si playerId
     * coincide con el solicitante (salida) o no (expulsión, que exige ser
     * administrador).
     */
    @DeleteMapping("/{communityId}/members/{playerId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long communityId,
            @PathVariable Long playerId,
            @RequestHeader("X-Player-Id") Long requesterId) { // TODO: Replace with SecurityContext

        communityService.removeMember(communityId, playerId, requesterId);
        return ResponseEntity.noContent().build();
    }
}
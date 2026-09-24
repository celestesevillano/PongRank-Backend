package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.Community.CommunityType;
import org.example.pongrankbackend.Community.dto.CommunityCreateRequestDTO;
import org.example.pongrankbackend.Community.dto.CommunityDetailResponseDTO;
import org.example.pongrankbackend.Community.dto.CommunityResponseDTO;
import org.example.pongrankbackend.Community.dto.CommunityUpdateRequestDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberAddRequestDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberResponseDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberRoleUpdateDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityRankingEntryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Contrato de negocio del módulo Community.
 *
 * Cubre los 11 endpoints del módulo, incluyendo la gestión de membresías:
 * CommunityMembership no tiene servicio propio porque no existe como concepto
 * independiente, siempre se opera en el contexto de una comunidad.
 *
 * El parámetro requesterId identifica al jugador que realiza la operación.
 * Se recibe como argumento en lugar de leerlo del SecurityContextHolder para
 * que el servicio no dependa de la capa web y sea testeable de forma aislada.
 * El Controller lo extraerá del token JWT cuando la seguridad esté integrada.
 */
public interface CommunityService {

    // ---------- Comunidades ----------

    /**
     * EP1 - POST /api/v1/communities
     * Crea la comunidad y, en la misma transacción, la membresía del creador
     * con rol COMMUNITY_ADMIN.
     *
     * @throws org.example.pongrankbackend.common.exception.DuplicateResourceException si el nombre ya existe (409)
     */
    CommunityResponseDTO createCommunity(CommunityCreateRequestDTO dto, Long requesterId);

    /**
     * EP2 - GET /api/v1/communities
     * Búsqueda paginada de comunidades ACTIVE con filtros opcionales.
     * Los conteos de miembros se resuelven en una sola consulta agrupada
     * para evitar el problema N+1.
     */
    Page<CommunityResponseDTO> searchCommunities(String name, CommunityType type,
                                                 Pageable pageable, Long requesterId);

    /**
     * EP3 - GET /api/v1/communities/me
     * Comunidades donde el solicitante tiene una membresía ACTIVE,
     * incluyendo su rol en cada una.
     */
    List<CommunityResponseDTO> getMyCommunities(Long requesterId);

    /**
     * EP4 - GET /api/v1/communities/{id}
     * Ficha completa: datos del creador, total de miembros y contexto del
     * solicitante (si es miembro y con qué rol).
     *
     * @throws org.example.pongrankbackend.common.exception.ResourceNotFoundException si la comunidad no existe (404)
     */
    CommunityDetailResponseDTO getCommunityById(Long communityId, Long requesterId);

    /**
     * EP5 - PUT /api/v1/communities/{id}
     * Actualiza nombre y descripción. Solo administradores activos.
     * No permite cambiar el tipo ni el estado de la comunidad.
     *
     * @throws org.example.pongrankbackend.common.exception.ResourceNotFoundException si no existe (404)
     * @throws org.example.pongrankbackend.common.exception.UnauthorizedActionException si no es admin (403)
     * @throws org.example.pongrankbackend.common.exception.InvalidOperationException si está archivada (409)
     * @throws org.example.pongrankbackend.common.exception.DuplicateResourceException si el nombre pertenece a otra (409)
     */
    CommunityResponseDTO updateCommunity(Long communityId, CommunityUpdateRequestDTO dto, Long requesterId);

    /**
     * EP6 - DELETE /api/v1/communities/{id}
     * Borrado lógico: cambia el estado a ARCHIVED conservando membresías y
     * partidos históricos. Solo administradores activos.
     *
     * No devuelve nada: el Controller responde 204 No Content.
     */
    void archiveCommunity(Long communityId, Long requesterId);

    // ---------- Membresías ----------

    /**
     * EP7 - GET /api/v1/communities/{id}/members
     * Lista paginada de miembros ACTIVE con sus datos de jugador.
     */
    Page<CommunityMemberResponseDTO> getCommunityMembers(Long communityId, Pageable pageable, Long requesterId);

    /**
     * EP8 - GET /api/v1/communities/{id}/ranking
     * Ranking interno ordenado por rating Glicko-2 descendente.
     * Es el ranking "dentro de la comunidad", distinto del ranking global
     * que corresponde al módulo Player.
     */
    Page<CommunityRankingEntryDTO> getCommunityRanking(Long communityId, Pageable pageable, Long requesterId);

    /**
     * EP9 - POST /api/v1/communities/{id}/members
     * Dos comportamientos según el DTO:
     *  - playerId null  -> el solicitante se une a sí mismo.
     *  - playerId       -> un administrador agrega a ese jugador.
     *
     * Si existe una membresía INACTIVE se reactiva en lugar de insertar una
     * nueva, porque el constraint uk_player_community impide duplicados.
     *
     * @throws org.example.pongrankbackend.common.exception.InvalidOperationException si la comunidad está archivada (409)
     * @throws org.example.pongrankbackend.common.exception.DuplicateResourceException si ya es miembro activo (409)
     * @throws org.example.pongrankbackend.common.exception.UnauthorizedActionException si agrega a otro sin ser admin (403)
     */
    CommunityMemberResponseDTO addMember(Long communityId, CommunityMemberAddRequestDTO dto, Long requesterId);

    /**
     * EP10 - PATCH /api/v1/communities/{id}/members/{playerId}/role
     * Promueve o degrada a un miembro. Solo administradores activos.
     *
     * Es el endpoint que permite al único administrador delegar antes de
     * salir de la comunidad.
     *
     * @throws org.example.pongrankbackend.common.exception.InvalidOperationException si la degradación dejaría la comunidad sin administradores (409)
     */
    CommunityMemberResponseDTO updateMemberRole(Long communityId, Long playerId,
                                                CommunityMemberRoleUpdateDTO dto, Long requesterId);

    /**
     * EP11 - DELETE /api/v1/communities/{id}/members/{playerId}
     * Salida voluntaria (playerId es el propio solicitante) o expulsión
     * (requiere ser administrador). Cambia el estado a INACTIVE conservando
     * la fila y su historial.
     *
     * Reglas especiales:
     *  - Si es el último administrador y quedan otros miembros, se rechaza (409).
     *  - Si es el último miembro, se permite y la comunidad se archiva.
     *
     * No devuelve nada: el Controller responde 204 No Content.
     */
    void removeMember(Long communityId, Long playerId, Long requesterId);
}
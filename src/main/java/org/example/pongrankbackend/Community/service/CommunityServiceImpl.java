package org.example.pongrankbackend.Community.service;
import org.example.pongrankbackend.Community.CommunityType;
import org.example.pongrankbackend.Community.dto.CommunityCreateRequestDTO;
import org.example.pongrankbackend.Community.dto.CommunityDetailResponseDTO;
import org.example.pongrankbackend.Community.dto.CommunityResponseDTO;
import org.example.pongrankbackend.Community.dto.CommunityUpdateRequestDTO;
import org.example.pongrankbackend.common.exception.DuplicateResourceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.Community.repository.CommunityRepository;
import org.example.pongrankbackend.CommunityMembership.CommunityMembership;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.CommunityMembership.MembershipStatus;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberResponseDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityRankingEntryDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberAddRequestDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberRoleUpdateDTO;
import java.time.LocalDateTime;
import org.example.pongrankbackend.CommunityMembership.repository.CommunityMembershipRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.InvalidOperationException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageImpl;
import java.util.ArrayList;


/**
 * Implementación de las reglas de negocio del módulo Community.
 *
 * @Transactional(readOnly = true) a nivel de clase: todos los métodos son de
 * solo lectura por defecto, lo que permite a Hibernate optimizar (sin dirty
 * checking). Los métodos que escriben lo sobrescriben con @Transactional.
 * Es el mismo patrón que usa PlayerServiceImpl.
 */
@Service
@Transactional(readOnly = true)
public class CommunityServiceImpl implements CommunityService {

    // Inyección por constructor, sin @Autowired ni campos mutables.
    // La rúbrica lo exige (3.3) y además permite declarar los campos final,
    // garantizando que las dependencias nunca cambian tras la construcción.
    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository membershipRepository;
    private final PlayerRepository playerRepository;
    private final ModelMapper modelMapper;

    public CommunityServiceImpl(CommunityRepository communityRepository,
                                CommunityMembershipRepository membershipRepository,
                                PlayerRepository playerRepository,
                                ModelMapper modelMapper) {
        this.communityRepository = communityRepository;
        this.membershipRepository = membershipRepository;
        this.playerRepository = playerRepository;
        this.modelMapper = modelMapper;
    }

    // =====================================================================
    // Métodos privados de carga
    // =====================================================================

    /**
     * Carga una comunidad o lanza 404.
     *
     * Se usa en las lecturas (EP4, EP7, EP8) y en las escrituras que no
     * modifican membresías (EP5, EP6). No bloquea la fila.
     */
    private Community loadCommunity(Long communityId) {
        return communityRepository.findById(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));
    }

    /**
     * Carga una comunidad bloqueando su fila para escritura.
     *
     * Se usa en EP9, EP10 y EP11, donde varias peticiones simultáneas podrían
     * leer el mismo conteo de administradores y dejar la comunidad sin ninguno.
     * El bloqueo obliga a la segunda petición a esperar a que la primera
     * confirme su transacción, de modo que cuente el valor ya actualizado.
     */
    private Community loadCommunityForUpdate(Long communityId) {
        return communityRepository.findByIdForUpdate(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));
    }

    /**
     * Carga un jugador o lanza 404.
     * Se usa para resolver el creador (EP1) y el jugador agregado por un
     * administrador (EP9).
     */
    private Player loadPlayer(Long playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", playerId));
    }

    /**
     * Busca la membresía por el par (comunidad, jugador) o lanza 404.
     *
     * Buscar siempre por la pareja evita que alguien modifique la membresía
     * de otra comunidad pasando un id ajeno en la URL. Si no existe se
     * responde 404 y no 403: un 403 confirmaría que ese recurso existe.
     */
    private CommunityMembership loadMembership(Long communityId, Long playerId) {
        return membershipRepository.findByCommunityIdAndPlayerId(communityId, playerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membership not found for player " + playerId + " in community " + communityId));
    }

    // =====================================================================
    // Métodos privados de validación
    // =====================================================================

    /**
     * Verifica que el solicitante sea administrador ACTIVO de la comunidad.
     *
     * El permiso se comprueba contra la membresía y nunca contra
     * Community.creator: el fundador puede haber salido de la comunidad,
     * en cuyo caso ya no debe conservar privilegios.
     */
    private void verifyRequesterIsAdmin(Long communityId, Long requesterId) {
        boolean isAdmin = membershipRepository.existsByCommunityIdAndPlayerIdAndRoleAndStatus(
                communityId, requesterId, CommunityRole.COMMUNITY_ADMIN, MembershipStatus.ACTIVE);

        if (!isAdmin) {
            throw new UnauthorizedActionException(
                    "Only community administrators can perform this action");
        }
    }

    /**
     * Rechaza la operación si la comunidad está archivada.
     *
     * Una comunidad ARCHIVED queda en modo solo lectura: no admite ediciones,
     * ingresos ni cambios de rol. La única escritura permitida es salir,
     * para que ningún miembro quede atrapado en ella.
     */
    private void verifyCommunityIsActive(Community community) {
        if (community.getStatus() == CommunityStatus.ARCHIVED) {
            throw new InvalidOperationException(
                    "Community is archived and cannot be modified");
        }
    }

    /**
     * Regla central del módulo: una comunidad nunca puede quedarse sin
     * administradores activos.
     *
     * Se invoca antes de degradar (EP10) o dar de baja (EP11) a un
     * administrador. Debe ejecutarse después de loadCommunityForUpdate,
     * porque sin el bloqueo dos peticiones simultáneas podrían leer el
     * mismo conteo y ambas darse por válidas.
     */
    private void verifyNotLastAdmin(Long communityId, CommunityMembership target) {
        boolean targetIsActiveAdmin = target.getRole() == CommunityRole.COMMUNITY_ADMIN
                && target.getStatus() == MembershipStatus.ACTIVE;

        if (!targetIsActiveAdmin) {
            return;
        }

        long activeAdmins = membershipRepository.countByCommunityIdAndRoleAndStatus(
                communityId, CommunityRole.COMMUNITY_ADMIN, MembershipStatus.ACTIVE);

        if (activeAdmins <= 1) {
            throw new InvalidOperationException(
                    "Cannot remove the last administrator. Promote another member first");
        }
    }

    // =====================================================================
    // Métodos privados de mapeo
    // =====================================================================

    /**
     * Consulta el rol del solicitante en una comunidad.
     * Devuelve null si no es miembro activo, lo que el frontend interpreta
     * como "puede unirse".
     */
    private CommunityRole findRequesterRole(Long communityId, Long requesterId) {
        return membershipRepository.findByCommunityIdAndPlayerId(communityId, requesterId)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .map(CommunityMembership::getRole)
                .orElse(null);
    }

    /**
     * Resuelve los conteos de miembros de varias comunidades en una sola
     * consulta agrupada, evitando el problema N+1.
     *
     * La consulta devuelve filas Object[] donde la posición 0 es el id de la
     * comunidad y la 1 el conteo; aquí se convierten a un Map para poder
     * consultarlas por id al construir cada DTO.
     */
    private Map<Long, Long> countMembersByCommunity(List<Long> communityIds) {
        if (communityIds.isEmpty()) {
            return new HashMap<>();
        }

        return membershipRepository
                .countActiveMembersByCommunityIds(communityIds, MembershipStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));
    }

    /**
     * Construye el DTO de miembro navegando la relación con Player.
     *
     * Se construye a mano con el Builder en lugar de usar ModelMapper porque
     * los datos provienen de dos entidades: la membresía y su jugador
     * asociado. Ser explícito evita mapeos silenciosos que dejarían campos
     * en null sin avisar.
     */
    private CommunityMemberResponseDTO toMemberDto(CommunityMembership membership) {
        Player player = membership.getPlayer();

        return CommunityMemberResponseDTO.builder()
                .membershipId(membership.getId())
                .playerId(player.getId())
                .playerName(player.getName())
                .ratingGlicko(player.getRatingGlicko())
                .role(membership.getRole())
                .status(membership.getStatus())
                .joinedAt(membership.getJoinedAt())
                .build();
    }

    /**
     * Construye una fila del ranking.
     *
     * La posición no la calcula la base de datos: se deriva del número de
     * página y del índice dentro de ella. En la página 2 con tamaño 20, el
     * primer elemento ocupa la posición 21.
     */
    private CommunityRankingEntryDTO toRankingDto(CommunityMembership membership, int position) {
        Player player = membership.getPlayer();

        return CommunityRankingEntryDTO.builder()
                .position(position)
                .playerId(player.getId())
                .playerName(player.getName())
                .ratingGlicko(player.getRatingGlicko())
                .ratingDeviation(player.getRatingDeviation())
                .build();
    }

    // =====================================================================
    // EP1 - EP6: Comunidades
    // =====================================================================

    /**
     * EP1 - Crea la comunidad y la membresía de su creador.
     *
     * Ambos INSERT viajan en la misma transacción. Sin @Transactional, un
     * fallo al crear la membresía dejaría una comunidad sin ningún
     * administrador: nadie podría editarla ni archivarla, y su nombre
     * quedaría ocupado para siempre por el constraint unique.
     */
    @Override
    @Transactional
    public CommunityResponseDTO createCommunity(CommunityCreateRequestDTO dto, Long requesterId) {
        String name = dto.getName().trim();

        // Verificación previa: da un mensaje claro al usuario.
        // La garantía real contra duplicados es el constraint unique de la
        // base, que se captura más abajo.
        if (communityRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("Community name already in use: " + name);
        }

        Player creator = loadPlayer(requesterId);
        Community community = buildCommunity(dto, name, creator);

        try {
            Community saved = communityRepository.save(community);
            createAdminMembership(saved, creator);
            return toCommunityDto(saved, CommunityRole.COMMUNITY_ADMIN, 1L);
        } catch (DataIntegrityViolationException ex) {
            // Se llega aquí si otra petición creó el mismo nombre entre la
            // verificación anterior y este INSERT. El constraint la detiene.
            throw new DuplicateResourceException("Community name already in use: " + name);
        }
    }

    /**
     * EP2 - Búsqueda paginada de comunidades activas.
     *
     * Los conteos de miembros y los roles del solicitante se resuelven en
     * consultas agrupadas fuera del bucle, no comunidad por comunidad.
     */
    @Override
    public Page<CommunityResponseDTO> searchCommunities(String name, CommunityType type,
                                                        Pageable pageable, Long requesterId) {
        // Un nombre nulo se traduce a cadena vacía para que LIKE '%%'
        // coincida con todo_, evitando el parámetro sin tipo en PostgreSQL.
        String nameFilter = (name == null) ? "" : name.trim();

        Page<Community> page = communityRepository.searchCommunities(
                CommunityStatus.ACTIVE, nameFilter, type, pageable);

        List<Long> ids = page.getContent().stream().map(Community::getId).toList();
        Map<Long, Long> counts = countMembersByCommunity(ids);

        // map() sobre el Page conserva la información de paginación
        // (total de elementos, número de página) y solo transforma el contenido.
        return page.map(community -> toCommunityDto(
                community,
                findRequesterRole(community.getId(), requesterId),
                counts.getOrDefault(community.getId(), 0L)));
    }

    /**
     * EP3 - Comunidades donde el solicitante es miembro activo.
     *
     * Sin paginar: un jugador pertenece a unas pocas comunidades, no a cientos.
     * La consulta trae la comunidad con JOIN FETCH para evitar el N+1.
     */
    @Override
    public List<CommunityResponseDTO> getMyCommunities(Long requesterId) {
        List<CommunityMembership> memberships = membershipRepository
                .findByPlayerIdWithCommunity(requesterId, MembershipStatus.ACTIVE);

        List<Long> ids = memberships.stream()
                .map(m -> m.getCommunity().getId())
                .toList();
        Map<Long, Long> counts = countMembersByCommunity(ids);

        return memberships.stream()
                .map(m -> toCommunityDto(
                        m.getCommunity(),
                        m.getRole(),
                        counts.getOrDefault(m.getCommunity().getId(), 0L)))
                .toList();
    }

    /**
     * EP4 - Ficha completa de una comunidad.
     *
     * Usa findByIdWithCreator para traer al fundador en la misma consulta:
     * creator es LAZY y accederlo después dispararía una consulta extra.
     *
     * Las comunidades archivadas sí se devuelven: se ocultan de la búsqueda
     * general, pero su ficha sigue siendo consultable como histórico.
     */
    @Override
    public CommunityDetailResponseDTO getCommunityById(Long communityId, Long requesterId) {
        Community community = communityRepository.findByIdWithCreator(communityId)
                .orElseThrow(() -> new ResourceNotFoundException("Community", communityId));

        CommunityRole myRole = findRequesterRole(communityId, requesterId);
        long memberCount = membershipRepository
                .countByCommunityIdAndStatus(communityId, MembershipStatus.ACTIVE);

        return buildDetailDto(community, myRole, memberCount);
    }

    /**
     * EP5 - Actualiza nombre y descripción. Solo administradores activos.
     *
     * No permite cambiar el tipo (una universidad no se convierte en parque)
     * ni el estado, que solo cambia mediante EP6.
     */
    @Override
    @Transactional
    public CommunityResponseDTO updateCommunity(Long communityId, CommunityUpdateRequestDTO dto,
                                                Long requesterId) {
        Community community = loadCommunity(communityId);
        verifyCommunityIsActive(community);
        verifyRequesterIsAdmin(communityId, requesterId);

        String name = dto.getName().trim();

        // IdNot excluye a la propia comunidad: al conservar su nombre actual
        // no debe chocar consigo misma.
        if (communityRepository.existsByNameIgnoreCaseAndIdNot(name, communityId)) {
            throw new DuplicateResourceException("Community name already in use: " + name);
        }

        community.setName(name);
        community.setDescription(dto.getDescription());

        long memberCount = membershipRepository
                .countByCommunityIdAndStatus(communityId, MembershipStatus.ACTIVE);

        return toCommunityDto(community, CommunityRole.COMMUNITY_ADMIN, memberCount);
    }

    /**
     * EP6 - Borrado lógico: la comunidad pasa a ARCHIVED.
     *
     * No se ejecuta repository.delete() porque Match guarda community_id:
     * un borrado físico rompería la integridad referencial y dejaría huérfano
     * el historial de partidos que alimenta el rating Glicko-2.
     *
     * Las membresías se conservan intactas.
     */
    @Override
    @Transactional
    public void archiveCommunity(Long communityId, Long requesterId) {
        Community community = loadCommunity(communityId);
        verifyCommunityIsActive(community);
        verifyRequesterIsAdmin(communityId, requesterId);

        community.setStatus(CommunityStatus.ARCHIVED);
    }



    /**
     * Construye la entidad a partir del DTO de creación.
     *
     * Se usa el Builder y no ModelMapper porque hay que asignar el creador
     * resuelto desde el token: aceptarlo desde el cliente permitiría crear
     * comunidades a nombre de otro jugador.
     */
    private Community buildCommunity(CommunityCreateRequestDTO dto, String name, Player creator) {
        return Community.builder()
                .name(name)
                .description(dto.getDescription())
                .communityType(dto.getCommunityType())
                .status(CommunityStatus.ACTIVE)
                .creator(creator)
                .build();
    }

    /**
     * Registra al fundador como administrador activo de su comunidad.
     *
     * Este es el paso que garantiza que toda comunidad nace con gobierno.
     * Debe ocurrir dentro de la misma transacción que la creación.
     */
    private void createAdminMembership(Community community, Player creator) {
        CommunityMembership membership = CommunityMembership.builder()
                .community(community)
                .player(creator)
                .role(CommunityRole.COMMUNITY_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        membershipRepository.save(membership);
    }

    /**
     * Mapea la entidad al DTO de listado y completa los campos que ModelMapper
     * no puede resolver: el conteo de miembros viene de una consulta agregada
     * y el rol depende de quién realiza la petición.
     */
    private CommunityResponseDTO toCommunityDto(Community community, CommunityRole myRole,
                                                long memberCount) {
        CommunityResponseDTO dto = modelMapper.map(community, CommunityResponseDTO.class);
        dto.setMyRole(myRole);
        dto.setMemberCount(memberCount);
        return dto;
    }

    /**
     * Mapea la ficha detallada, aplanando los datos del creador.
     *
     * Nunca se expone el objeto Player completo: arrastraría el hash de la
     * contraseña, el email y todas sus relaciones perezosas.
     */
    private CommunityDetailResponseDTO buildDetailDto(Community community, CommunityRole myRole,
                                                      long memberCount) {
        return CommunityDetailResponseDTO.builder()
                .id(community.getId())
                .name(community.getName())
                .description(community.getDescription())
                .communityType(community.getCommunityType())
                .status(community.getStatus())
                .memberCount(memberCount)
                .creatorId(community.getCreator().getId())
                .creatorName(community.getCreator().getName())
                .isMember(myRole != null)
                .myRole(myRole)
                .createdAt(community.getCreatedAt())
                .build();
    }


    // =====================================================================
    // EP7 - EP11: Membresías
    // =====================================================================

    /**
     * EP7 - Lista paginada de miembros activos.
     *
     * La consulta usa JOIN FETCH del jugador: sin él, listar 20 miembros
     * ejecutaría 21 consultas al leer el nombre de cada uno.
     */
    @Override
    public Page<CommunityMemberResponseDTO> getCommunityMembers(Long communityId, Pageable pageable,
                                                                Long requesterId) {
        // Valida la existencia antes de consultar: si la comunidad no existe,
        // la consulta devolvería una página vacía en vez del 404 correcto.
        loadCommunity(communityId);

        return membershipRepository
                .findMembersWithPlayer(communityId, MembershipStatus.ACTIVE, pageable)
                .map(this::toMemberDto);
    }

    /**
     * EP8 - Ranking interno ordenado por rating Glicko-2.
     *
     * Es el ranking "dentro de la comunidad" que el mockup muestra como
     * "#7 en UTEC", distinto del ranking global del módulo Player.
     *
     * La posición se calcula aquí y no en la base de datos: depende de la
     * página solicitada. En la página 2 con tamaño 20, la primera fila
     * ocupa la posición 21.
     */
    @Override
    public Page<CommunityRankingEntryDTO> getCommunityRanking(Long communityId, Pageable pageable,
                                                              Long requesterId) {
        loadCommunity(communityId);

        Page<CommunityMembership> page = membershipRepository
                .findRankingWithPlayer(communityId, MembershipStatus.ACTIVE, pageable);

        int offset = (int) pageable.getOffset();
        List<CommunityRankingEntryDTO> entries = buildRankingEntries(page.getContent(), offset);

        return new PageImpl<>(entries, pageable, page.getTotalElements());
    }

    /**
     * EP9 - Ingreso a la comunidad.
     *
     * Dos comportamientos según el DTO:
     *  - playerId null -> el solicitante se une a sí mismo.
     *  - playerId      -> un administrador agrega a ese jugador.
     *
     * Usa bloqueo pesimista porque modifica la composición de la comunidad.
     */
    @Override
    @Transactional
    public CommunityMemberResponseDTO addMember(Long communityId, CommunityMemberAddRequestDTO dto,
                                                Long requesterId) {
        Community community = loadCommunityForUpdate(communityId);
        verifyCommunityIsActive(community);

        Long targetId = resolveTargetPlayer(communityId, dto, requesterId);

        // Se busca en cualquier estado, no solo ACTIVE: el constraint
        // uk_player_community impide insertar una segunda fila para el mismo
        // par, así que quien vuelve debe reactivar la existente.
        return membershipRepository.findByCommunityIdAndPlayerId(communityId, targetId)
                .map(this::reactivateMembership)
                .orElseGet(() -> createMembership(community, targetId));
    }

    /**
     * EP10 - Promueve o degrada a un miembro. Solo administradores activos.
     *
     * Es el endpoint que permite al único administrador delegar antes de
     * salir: primero promueve a otro, y entonces EP11 le deja abandonar.
     */
    @Override
    @Transactional
    public CommunityMemberResponseDTO updateMemberRole(Long communityId, Long playerId,
                                                       CommunityMemberRoleUpdateDTO dto,
                                                       Long requesterId) {
        Community community = loadCommunityForUpdate(communityId);
        verifyCommunityIsActive(community);
        verifyRequesterIsAdmin(communityId, requesterId);

        CommunityMembership membership = loadActiveMembership(communityId, playerId);

        // Solo se valida al degradar. Promover nunca deja sin administradores.
        if (dto.getRole() != CommunityRole.COMMUNITY_ADMIN) {
            verifyNotLastAdmin(communityId, membership);
        }

        membership.setRole(dto.getRole());
        return toMemberDto(membership);
    }

    /**
     * EP11 - Salida voluntaria o expulsión.
     *
     * La membresía pasa a INACTIVE conservando la fila y su historial.
     * Si quien sale es el último miembro, la comunidad se archiva en lugar
     * de quedar activa y vacía sin nadie que pueda gestionarla.
     */
    @Override
    @Transactional
    public void removeMember(Long communityId, Long playerId, Long requesterId) {
        Community community = loadCommunityForUpdate(communityId);

        // Salir de una comunidad archivada sí está permitido: nadie debe
        // quedar atrapado en ella. Por eso no se llama verifyCommunityIsActive.
        boolean isSelfRemoval = playerId.equals(requesterId);
        if (!isSelfRemoval) {
            verifyRequesterIsAdmin(communityId, requesterId);
        }

        CommunityMembership membership = loadActiveMembership(communityId, playerId);

        long activeMembers = membershipRepository
                .countByCommunityIdAndStatus(communityId, MembershipStatus.ACTIVE);

        // El último miembro puede salir siempre: no hay nadie a quien delegar.
        if (activeMembers > 1) {
            verifyNotLastAdmin(communityId, membership);
        }

        deactivateMembership(membership);

        if (activeMembers == 1) {
            community.setStatus(CommunityStatus.ARCHIVED);
        }
    }


    /**
     * Determina a quién se agrega y valida el permiso correspondiente.
     *
     * Sin playerId el solicitante se une a sí mismo, lo que no requiere
     * permisos especiales. Con playerId se está agregando a otra persona,
     * y eso exige ser administrador activo.
     */
    private Long resolveTargetPlayer(Long communityId, CommunityMemberAddRequestDTO dto,
                                     Long requesterId) {
        if (dto == null || dto.getPlayerId() == null) {
            return requesterId;
        }

        if (!dto.getPlayerId().equals(requesterId)) {
            verifyRequesterIsAdmin(communityId, requesterId);
        }

        return dto.getPlayerId();
    }

    /**
     * Reactiva una membresía previa o rechaza el ingreso si ya está activa.
     *
     * Reactivar en lugar de insertar es obligatorio: el constraint
     * uk_player_community impide dos filas para el mismo par.
     * joinedAt no se toca (es updatable = false), conservando la fecha del
     * primer ingreso como dato histórico.
     */
    private CommunityMemberResponseDTO reactivateMembership(CommunityMembership membership) {
        if (membership.getStatus() == MembershipStatus.ACTIVE) {
            throw new DuplicateResourceException("Player is already a member of this community");
        }

        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setLeftAt(null);
        return toMemberDto(membership);
    }

    /**
     * Crea una membresía nueva con rol MEMBER.
     * Solo el fundador nace como administrador; el resto debe ser promovido.
     */
    private CommunityMemberResponseDTO createMembership(Community community, Long playerId) {
        CommunityMembership membership = CommunityMembership.builder()
                .community(community)
                .player(loadPlayer(playerId))
                .role(CommunityRole.MEMBER)
                .status(MembershipStatus.ACTIVE)
                .build();

        return toMemberDto(membershipRepository.save(membership));
    }

    /**
     * Marca la membresía como INACTIVE registrando la fecha de salida.
     *
     * El rol no se modifica: si el jugador reingresa, lo recupera.
     * El estado es la única fuente de verdad sobre la pertenencia, y todos
     * los conteos de administradores filtran por ACTIVE.
     */
    private void deactivateMembership(CommunityMembership membership) {
        membership.setStatus(MembershipStatus.INACTIVE);
        membership.setLeftAt(LocalDateTime.now());
    }

    /**
     * Carga una membresía exigiendo que esté activa.
     *
     * Se usa en EP10 y EP11: no tiene sentido cambiar el rol o dar de baja
     * a alguien que ya no pertenece a la comunidad.
     */
    private CommunityMembership loadActiveMembership(Long communityId, Long playerId) {
        CommunityMembership membership = loadMembership(communityId, playerId);

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ResourceNotFoundException(
                    "Player " + playerId + " is not an active member of community " + communityId);
        }

        return membership;
    }

    /**
     * Convierte las membresías del ranking en DTOs numerados.
     * El offset traslada el índice local de la página a la posición global.
     */
    private List<CommunityRankingEntryDTO> buildRankingEntries(List<CommunityMembership> memberships,
                                                               int offset) {
        List<CommunityRankingEntryDTO> entries = new ArrayList<>();

        for (int i = 0; i < memberships.size(); i++) {
            entries.add(toRankingDto(memberships.get(i), offset + i + 1));
        }

        return entries;
    }
}
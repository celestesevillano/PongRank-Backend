package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.Community.CommunityType;
import org.example.pongrankbackend.Community.dto.CommunityCreateRequestDTO;
import org.example.pongrankbackend.Community.dto.CommunityResponseDTO;
import org.example.pongrankbackend.Community.dto.CommunityUpdateRequestDTO;
import org.example.pongrankbackend.Community.repository.CommunityRepository;
import org.example.pongrankbackend.CommunityMembership.CommunityMembership;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.CommunityMembership.MembershipStatus;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberAddRequestDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberRoleUpdateDTO;
import org.example.pongrankbackend.CommunityMembership.repository.CommunityMembershipRepository;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.service.MembershipService;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommunityServiceImpl")
class CommunityServiceImplTest {

    private static final Long COMMUNITY_ID = 10L;
    private static final Long CREATOR_ID = 1L;
    private static final Long OTHER_PLAYER_ID = 99L;

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private CommunityMembershipRepository membershipRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private MembershipService membershipService;

    @InjectMocks
    private CommunityServiceImpl communityService;

    private Player creator;
    private Community community;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(communityService, "maxCreatedFreemium", 1);
        ReflectionTestUtils.setField(communityService, "maxCreatedBasic", 5);
        ReflectionTestUtils.setField(communityService, "maxCreatedPro", 8);
        ReflectionTestUtils.setField(communityService, "maxCreatedEnterprise", 8);
        ReflectionTestUtils.setField(communityService, "maxTotalFreemium", 2);
        ReflectionTestUtils.setField(communityService, "maxTotalBasic", 7);
        ReflectionTestUtils.setField(communityService, "maxTotalPro", 10);
        ReflectionTestUtils.setField(communityService, "maxTotalEnterprise", 10);

        lenient().when(membershipService.getActivePlan(anyLong())).thenReturn(MembershipPlan.FREEMIUM);

        creator = Player.builder()
                .id(CREATOR_ID)
                .name("Andres")
                .email("andres@utec.edu.pe")
                .ratingGlicko(1500.0)
                .ratingDeviation(350.0)
                .build();

        community = Community.builder()
                .id(COMMUNITY_ID)
                .name("UTEC")
                .communityType(CommunityType.UNIVERSITY)
                .status(CommunityStatus.ACTIVE)
                .creator(creator)
                .build();
    }

    // ---------- Helpers ----------

    private CommunityCreateRequestDTO createRequest() {
        return CommunityCreateRequestDTO.builder()
                .name("UTEC")
                .description("Comunidad UTEC")
                .communityType(CommunityType.UNIVERSITY)
                .build();
    }

    private CommunityUpdateRequestDTO updateRequest() {
        return CommunityUpdateRequestDTO.builder()
                .name("UTEC Renombrada")
                .description("Nueva descripcion")
                .build();
    }

    private CommunityMembership membership(CommunityRole role, MembershipStatus status) {
        return CommunityMembership.builder()
                .id(100L)
                .community(community)
                .player(creator)
                .role(role)
                .status(status)
                .build();
    }

    private void givenRequesterIsAdmin(boolean isAdmin) {
        when(membershipRepository.existsByCommunityIdAndPlayerIdAndRoleAndStatus(
                anyLong(), anyLong(), any(), any())).thenReturn(isAdmin);
    }

    private void givenIsPremium(boolean isPremium) {
        when(membershipService.getActivePlan(anyLong())).thenReturn(isPremium ? MembershipPlan.BASIC : MembershipPlan.FREEMIUM);
    }

    private void givenActiveCommunitiesCreated(long count) {
        when(communityRepository.countByCreatorIdAndStatus(anyLong(), eq(CommunityStatus.ACTIVE)))
                .thenReturn(count);
    }

    private void givenActiveMemberships(long count) {
        when(membershipRepository.countByPlayerIdAndRoleAndStatus(
                anyLong(), eq(CommunityRole.MEMBER), eq(MembershipStatus.ACTIVE))).thenReturn(count);
    }

    private void thenFailsWith(HttpStatus expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(expected);
    }

    // ---------- EP1 ----------

    @Nested
    @DisplayName("Creacion de comunidad")
    class CreateCommunity {

        @Test
        @DisplayName("registra al creador como administrador activo")
        void shouldCreateAdminMembershipWhenCommunityIsCreated() {
            when(communityRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
            when(playerRepository.findById(CREATOR_ID)).thenReturn(Optional.of(creator));
            when(communityRepository.save(any(Community.class))).thenReturn(community);
            when(modelMapper.map(any(Community.class), eq(CommunityResponseDTO.class)))
                    .thenReturn(new CommunityResponseDTO());

            communityService.createCommunity(createRequest(), CREATOR_ID);

            verify(membershipRepository).save(argThat(m ->
                    m.getRole() == CommunityRole.COMMUNITY_ADMIN
                            && m.getStatus() == MembershipStatus.ACTIVE
                            && m.getPlayer().getId().equals(CREATOR_ID)));
        }

        @Test
        @DisplayName("responde 409 cuando el nombre ya existe")
        void shouldThrowExceptionWhenCommunityNameIsDuplicated() {
            when(communityRepository.existsByNameIgnoreCase(anyString())).thenReturn(true);

            thenFailsWith(HttpStatus.CONFLICT,
                    () -> communityService.createCommunity(createRequest(), CREATOR_ID));

            verify(communityRepository, never()).save(any());
            verify(membershipRepository, never()).save(any());
        }

        @Test
        @DisplayName("responde 404 cuando el creador no existe")
        void shouldThrowExceptionWhenCreatorDoesNotExist() {
            when(communityRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
            when(playerRepository.findById(anyLong())).thenReturn(Optional.empty());

            thenFailsWith(HttpStatus.NOT_FOUND,
                    () -> communityService.createCommunity(createRequest(), OTHER_PLAYER_ID));

            verify(communityRepository, never()).save(any());
        }
    }

    // ---------- EP4, EP5, EP6 ----------

    @Nested
    @DisplayName("Lectura y administracion de comunidad")
    class ManageCommunity {

        @Test
        @DisplayName("responde 404 cuando la comunidad no existe")
        void shouldThrowExceptionWhenCommunityDoesNotExist() {
            when(communityRepository.findByIdWithCreator(anyLong())).thenReturn(Optional.empty());

            thenFailsWith(HttpStatus.NOT_FOUND,
                    () -> communityService.getCommunityById(999L, CREATOR_ID));
        }

        @Test
        @DisplayName("responde 403 cuando quien actualiza no es administrador")
        void shouldThrowExceptionWhenNonAdminUpdatesCommunity() {
            when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
            givenRequesterIsAdmin(false);

            thenFailsWith(HttpStatus.FORBIDDEN, () -> communityService.updateCommunity(
                    COMMUNITY_ID, updateRequest(), OTHER_PLAYER_ID));

            assertThat(community.getName()).isEqualTo("UTEC");
        }

        @Test
        @DisplayName("responde 409 cuando el nombre nuevo pertenece a otra comunidad")
        void shouldThrowExceptionWhenNewNameBelongsToAnotherCommunity() {
            when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
            givenRequesterIsAdmin(true);
            when(communityRepository.existsByNameIgnoreCaseAndIdNot(anyString(), anyLong()))
                    .thenReturn(true);

            thenFailsWith(HttpStatus.CONFLICT, () -> communityService.updateCommunity(
                    COMMUNITY_ID, updateRequest(), CREATOR_ID));

            assertThat(community.getName()).isEqualTo("UTEC");
        }

        @Test
        @DisplayName("archivar cambia el estado sin borrar el registro")
        void shouldSetStatusArchivedWhenCommunityIsDeleted() {
            when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
            givenRequesterIsAdmin(true);

            communityService.archiveCommunity(COMMUNITY_ID, CREATOR_ID);

            assertThat(community.getStatus()).isEqualTo(CommunityStatus.ARCHIVED);
            verify(communityRepository, never()).delete(any());
            verify(communityRepository, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("responde 409 al archivar una comunidad ya archivada")
        void shouldThrowExceptionWhenArchivingArchivedCommunity() {
            community.setStatus(CommunityStatus.ARCHIVED);
            when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));

            thenFailsWith(HttpStatus.CONFLICT,
                    () -> communityService.archiveCommunity(COMMUNITY_ID, CREATOR_ID));
        }
    }

    // ---------- EP9 ----------

    @Nested
    @DisplayName("Ingreso a la comunidad")
    class AddMember {

        @Test
        @DisplayName("reactiva la membresia previa en lugar de crear otra")
        void shouldReactivateMembershipWhenPlayerRejoinsCommunity() {
            CommunityMembership old = membership(CommunityRole.COMMUNITY_ADMIN,
                    MembershipStatus.INACTIVE);

            when(communityRepository.findByIdForUpdate(COMMUNITY_ID))
                    .thenReturn(Optional.of(community));
            when(membershipRepository.findByCommunityIdAndPlayerId(COMMUNITY_ID, CREATOR_ID))
                    .thenReturn(Optional.of(old));

            communityService.addMember(COMMUNITY_ID, new CommunityMemberAddRequestDTO(), CREATOR_ID);

            assertThat(old.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
            assertThat(old.getLeftAt()).isNull();
            assertThat(old.getRole()).isEqualTo(CommunityRole.COMMUNITY_ADMIN);
            verify(membershipRepository, never()).save(any());
        }

        @Test
        @DisplayName("responde 409 cuando el jugador ya es miembro activo")
        void shouldThrowExceptionWhenPlayerIsAlreadyActiveMember() {
            when(communityRepository.findByIdForUpdate(COMMUNITY_ID))
                    .thenReturn(Optional.of(community));
            when(membershipRepository.findByCommunityIdAndPlayerId(COMMUNITY_ID, CREATOR_ID))
                    .thenReturn(Optional.of(membership(CommunityRole.MEMBER, MembershipStatus.ACTIVE)));

            thenFailsWith(HttpStatus.CONFLICT, () -> communityService.addMember(
                    COMMUNITY_ID, new CommunityMemberAddRequestDTO(), CREATOR_ID));
        }

        @Test
        @DisplayName("responde 409 al unirse a una comunidad archivada")
        void shouldThrowExceptionWhenJoiningArchivedCommunity() {
            community.setStatus(CommunityStatus.ARCHIVED);
            when(communityRepository.findByIdForUpdate(COMMUNITY_ID))
                    .thenReturn(Optional.of(community));

            thenFailsWith(HttpStatus.CONFLICT, () -> communityService.addMember(
                    COMMUNITY_ID, new CommunityMemberAddRequestDTO(), CREATOR_ID));

            verify(membershipRepository, never()).save(any());
        }

        @Test
        @DisplayName("responde 403 cuando un no administrador agrega a otro jugador")
        void shouldThrowExceptionWhenNonAdminAddsAnotherPlayer() {
            when(communityRepository.findByIdForUpdate(COMMUNITY_ID))
                    .thenReturn(Optional.of(community));
            givenRequesterIsAdmin(false);

            CommunityMemberAddRequestDTO dto = CommunityMemberAddRequestDTO.builder()
                    .playerId(OTHER_PLAYER_ID)
                    .build();

            thenFailsWith(HttpStatus.FORBIDDEN,
                    () -> communityService.addMember(COMMUNITY_ID, dto, CREATOR_ID));

            verify(membershipRepository, never()).save(any());
        }
    }

    // ---------- Regla 1: limite de creacion (EP1) ----------

    @Nested
    @DisplayName("Limite de creacion segun plan")
    class CreationLimit {

        private void givenSuccessfulCreationPath() {
            when(communityRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
            when(playerRepository.findById(CREATOR_ID)).thenReturn(Optional.of(creator));
            when(communityRepository.save(any(Community.class))).thenReturn(community);
            when(modelMapper.map(any(Community.class), eq(CommunityResponseDTO.class)))
                    .thenReturn(new CommunityResponseDTO());
        }

        private void givenRejectedCreationPath() {
            when(communityRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
            when(playerRepository.findById(CREATOR_ID)).thenReturn(Optional.of(creator));
        }

        @Test
        @DisplayName("freemium: permite crear justo debajo del limite (0 de 1)")
        void freemium_JustBelowLimit_Allows() {
            givenIsPremium(false);
            givenActiveCommunitiesCreated(0L);
            givenSuccessfulCreationPath();

            communityService.createCommunity(createRequest(), CREATOR_ID);

            verify(communityRepository).save(any(Community.class));
        }

        @Test
        @DisplayName("freemium: rechaza justo en el limite (1 de 1)")
        void freemium_AtLimit_Rejects() {
            givenIsPremium(false);
            givenActiveCommunitiesCreated(1L);
            givenRejectedCreationPath();

            thenFailsWith(HttpStatus.FORBIDDEN,
                    () -> communityService.createCommunity(createRequest(), CREATOR_ID));

            verify(communityRepository, never()).save(any());
        }

        @Test
        @DisplayName("freemium: rechaza por encima del limite (2 de 1)")
        void freemium_AboveLimit_Rejects() {
            givenIsPremium(false);
            givenActiveCommunitiesCreated(2L);
            givenRejectedCreationPath();

            thenFailsWith(HttpStatus.FORBIDDEN,
                    () -> communityService.createCommunity(createRequest(), CREATOR_ID));

            verify(communityRepository, never()).save(any());
        }

        @Test
        @DisplayName("premium: permite crear justo debajo del limite (4 de 5)")
        void premium_JustBelowLimit_Allows() {
            givenIsPremium(true);
            givenActiveCommunitiesCreated(4L);
            givenSuccessfulCreationPath();

            communityService.createCommunity(createRequest(), CREATOR_ID);

            verify(communityRepository).save(any(Community.class));
        }

        @Test
        @DisplayName("premium: rechaza justo en el limite (5 de 5)")
        void premium_AtLimit_Rejects() {
            givenIsPremium(true);
            givenActiveCommunitiesCreated(5L);
            givenRejectedCreationPath();

            thenFailsWith(HttpStatus.FORBIDDEN,
                    () -> communityService.createCommunity(createRequest(), CREATOR_ID));

            verify(communityRepository, never()).save(any());
        }

        @Test
        @DisplayName("premium: rechaza por encima del limite (6 de 5)")
        void premium_AboveLimit_Rejects() {
            givenIsPremium(true);
            givenActiveCommunitiesCreated(6L);
            givenRejectedCreationPath();

            thenFailsWith(HttpStatus.FORBIDDEN,
                    () -> communityService.createCommunity(createRequest(), CREATOR_ID));

            verify(communityRepository, never()).save(any());
        }
    }

    // ---------- Regla 2: limite total de pertenencia (EP9) ----------

    @Nested
    @DisplayName("Limite total de pertenencia segun plan")
    class TotalMembershipLimit {

        private void givenRejoinAllowed() {
            CommunityMembership old = membership(CommunityRole.MEMBER, MembershipStatus.INACTIVE);
            when(communityRepository.findByIdForUpdate(COMMUNITY_ID)).thenReturn(Optional.of(community));
            when(membershipRepository.findByCommunityIdAndPlayerId(COMMUNITY_ID, CREATOR_ID))
                    .thenReturn(Optional.of(old));
        }

        private void givenJoinRejected() {
            when(communityRepository.findByIdForUpdate(COMMUNITY_ID)).thenReturn(Optional.of(community));
        }

        // freemium: max-total = 2 (creadas + member activo)

        @Test
        @DisplayName("freemium: permite unirse justo debajo del limite (1 de 2)")
        void freemium_JustBelowLimit_Allows() {
            givenIsPremium(false);
            givenActiveCommunitiesCreated(0L);
            givenActiveMemberships(1L);
            givenRejoinAllowed();

            communityService.addMember(COMMUNITY_ID, new CommunityMemberAddRequestDTO(), CREATOR_ID);

            verify(membershipRepository, never()).save(any());
        }

        @Test
        @DisplayName("freemium: rechaza justo en el limite (2 de 2)")
        void freemium_AtLimit_Rejects() {
            givenIsPremium(false);
            givenActiveCommunitiesCreated(1L);
            givenActiveMemberships(1L);
            givenJoinRejected();

            thenFailsWith(HttpStatus.FORBIDDEN, () -> communityService.addMember(
                    COMMUNITY_ID, new CommunityMemberAddRequestDTO(), CREATOR_ID));
        }

        @Test
        @DisplayName("freemium: rechaza por encima del limite (3 de 2)")
        void freemium_AboveLimit_Rejects() {
            givenIsPremium(false);
            givenActiveCommunitiesCreated(1L);
            givenActiveMemberships(2L);
            givenJoinRejected();

            thenFailsWith(HttpStatus.FORBIDDEN, () -> communityService.addMember(
                    COMMUNITY_ID, new CommunityMemberAddRequestDTO(), CREATOR_ID));
        }

        // premium: max-total = 7 (creadas + member activo) - mismo ejemplo del enunciado: 5 creadas, hasta 2 mas

        @Test
        @DisplayName("premium: permite unirse justo debajo del limite (5 creadas + 1 = 6 de 7)")
        void premium_JustBelowLimit_Allows() {
            givenIsPremium(true);
            givenActiveCommunitiesCreated(5L);
            givenActiveMemberships(1L);
            givenRejoinAllowed();

            communityService.addMember(COMMUNITY_ID, new CommunityMemberAddRequestDTO(), CREATOR_ID);

            verify(membershipRepository, never()).save(any());
        }

        @Test
        @DisplayName("premium: rechaza justo en el limite (5 creadas + 2 = 7 de 7)")
        void premium_AtLimit_Rejects() {
            givenIsPremium(true);
            givenActiveCommunitiesCreated(5L);
            givenActiveMemberships(2L);
            givenJoinRejected();

            thenFailsWith(HttpStatus.FORBIDDEN, () -> communityService.addMember(
                    COMMUNITY_ID, new CommunityMemberAddRequestDTO(), CREATOR_ID));
        }

        @Test
        @DisplayName("premium: rechaza por encima del limite (5 creadas + 3 = 8 de 7)")
        void premium_AboveLimit_Rejects() {
            givenIsPremium(true);
            givenActiveCommunitiesCreated(5L);
            givenActiveMemberships(3L);
            givenJoinRejected();

            thenFailsWith(HttpStatus.FORBIDDEN, () -> communityService.addMember(
                    COMMUNITY_ID, new CommunityMemberAddRequestDTO(), CREATOR_ID));
        }
    }

    // ---------- EP10, EP11 ----------

    @Nested
    @DisplayName("Regla del ultimo administrador")
    class LastAdminRule {

        @Test
        @DisplayName("responde 409 cuando el unico administrador intenta salir")
        void shouldThrowExceptionWhenLastAdminLeavesCommunity() {
            CommunityMembership admin = membership(CommunityRole.COMMUNITY_ADMIN,
                    MembershipStatus.ACTIVE);

            when(communityRepository.findByIdForUpdate(COMMUNITY_ID))
                    .thenReturn(Optional.of(community));
            when(membershipRepository.findByCommunityIdAndPlayerId(COMMUNITY_ID, CREATOR_ID))
                    .thenReturn(Optional.of(admin));
            when(membershipRepository.countByCommunityIdAndStatus(
                    COMMUNITY_ID, MembershipStatus.ACTIVE)).thenReturn(3L);
            when(membershipRepository.countByCommunityIdAndRoleAndStatus(
                    COMMUNITY_ID, CommunityRole.COMMUNITY_ADMIN, MembershipStatus.ACTIVE))
                    .thenReturn(1L);

            thenFailsWith(HttpStatus.CONFLICT,
                    () -> communityService.removeMember(COMMUNITY_ID, CREATOR_ID, CREATOR_ID));

            assertThat(admin.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
            assertThat(admin.getLeftAt()).isNull();
            assertThat(community.getStatus()).isEqualTo(CommunityStatus.ACTIVE);
        }

        @Test
        @DisplayName("permite salir cuando existe otro administrador activo")
        void shouldAllowAdminToLeaveWhenAnotherAdminExists() {
            CommunityMembership admin = membership(CommunityRole.COMMUNITY_ADMIN,
                    MembershipStatus.ACTIVE);

            when(communityRepository.findByIdForUpdate(COMMUNITY_ID))
                    .thenReturn(Optional.of(community));
            when(membershipRepository.findByCommunityIdAndPlayerId(COMMUNITY_ID, CREATOR_ID))
                    .thenReturn(Optional.of(admin));
            when(membershipRepository.countByCommunityIdAndStatus(
                    COMMUNITY_ID, MembershipStatus.ACTIVE)).thenReturn(3L);
            when(membershipRepository.countByCommunityIdAndRoleAndStatus(
                    COMMUNITY_ID, CommunityRole.COMMUNITY_ADMIN, MembershipStatus.ACTIVE))
                    .thenReturn(2L);

            communityService.removeMember(COMMUNITY_ID, CREATOR_ID, CREATOR_ID);

            assertThat(admin.getStatus()).isEqualTo(MembershipStatus.INACTIVE);
            assertThat(admin.getLeftAt()).isNotNull();
            assertThat(community.getStatus()).isEqualTo(CommunityStatus.ACTIVE);
        }

        @Test
        @DisplayName("el ultimo miembro puede salir y la comunidad se archiva")
        void shouldArchiveCommunityWhenLastMemberLeaves() {
            CommunityMembership admin = membership(CommunityRole.COMMUNITY_ADMIN,
                    MembershipStatus.ACTIVE);

            when(communityRepository.findByIdForUpdate(COMMUNITY_ID))
                    .thenReturn(Optional.of(community));
            when(membershipRepository.findByCommunityIdAndPlayerId(COMMUNITY_ID, CREATOR_ID))
                    .thenReturn(Optional.of(admin));
            when(membershipRepository.countByCommunityIdAndStatus(
                    COMMUNITY_ID, MembershipStatus.ACTIVE)).thenReturn(1L);

            communityService.removeMember(COMMUNITY_ID, CREATOR_ID, CREATOR_ID);

            assertThat(admin.getStatus()).isEqualTo(MembershipStatus.INACTIVE);
            assertThat(admin.getLeftAt()).isNotNull();
            assertThat(community.getStatus()).isEqualTo(CommunityStatus.ARCHIVED);
        }

        @Test
        @DisplayName("responde 409 cuando el unico administrador se degrada a si mismo")
        void shouldThrowExceptionWhenLastAdminDemotesHimself() {
            CommunityMembership admin = membership(CommunityRole.COMMUNITY_ADMIN,
                    MembershipStatus.ACTIVE);

            when(communityRepository.findByIdForUpdate(COMMUNITY_ID))
                    .thenReturn(Optional.of(community));
            givenRequesterIsAdmin(true);
            when(membershipRepository.findByCommunityIdAndPlayerId(COMMUNITY_ID, CREATOR_ID))
                    .thenReturn(Optional.of(admin));
            when(membershipRepository.countByCommunityIdAndRoleAndStatus(
                    COMMUNITY_ID, CommunityRole.COMMUNITY_ADMIN, MembershipStatus.ACTIVE))
                    .thenReturn(1L);

            CommunityMemberRoleUpdateDTO dto = CommunityMemberRoleUpdateDTO.builder()
                    .role(CommunityRole.MEMBER)
                    .build();

            thenFailsWith(HttpStatus.CONFLICT, () -> communityService.updateMemberRole(
                    COMMUNITY_ID, CREATOR_ID, dto, CREATOR_ID));

            assertThat(admin.getRole()).isEqualTo(CommunityRole.COMMUNITY_ADMIN);
        }

        @Test
        @DisplayName("promover a administrador no requiere validar el ultimo administrador")
        void shouldPromoteMemberWhenRequesterIsAdmin() {
            CommunityMembership member = membership(CommunityRole.MEMBER, MembershipStatus.ACTIVE);

            when(communityRepository.findByIdForUpdate(COMMUNITY_ID))
                    .thenReturn(Optional.of(community));
            givenRequesterIsAdmin(true);
            when(membershipRepository.findByCommunityIdAndPlayerId(COMMUNITY_ID, CREATOR_ID))
                    .thenReturn(Optional.of(member));

            CommunityMemberRoleUpdateDTO dto = CommunityMemberRoleUpdateDTO.builder()
                    .role(CommunityRole.COMMUNITY_ADMIN)
                    .build();

            communityService.updateMemberRole(COMMUNITY_ID, CREATOR_ID, dto, CREATOR_ID);

            assertThat(member.getRole()).isEqualTo(CommunityRole.COMMUNITY_ADMIN);
            verify(membershipRepository, never())
                    .countByCommunityIdAndRoleAndStatus(anyLong(), any(), any());
        }
    }
}
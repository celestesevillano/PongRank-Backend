package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.Community.CommunityType;
import org.example.pongrankbackend.Community.dto.CommunityDetailResponseDTO;
import org.example.pongrankbackend.CommunityMembership.CommunityMembership;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.CommunityMembership.MembershipStatus;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberResponseDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityRankingEntryDTO;
import org.example.pongrankbackend.Player.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityMapperTest {

    private final CommunityMapper mapper = new CommunityMapper(new ModelMapper());

    private Player player(Long id, String name, double rating) {
        return Player.builder().id(id).name(name).ratingGlicko(rating).ratingDeviation(120.0).build();
    }

    private Community community(Player creator) {
        return Community.builder()
                .id(10L)
                .name("UTEC")
                .communityType(CommunityType.UNIVERSITY)
                .status(CommunityStatus.ACTIVE)
                .creator(creator)
                .build();
    }

    @Test
    @DisplayName("toRankingEntries: numera las posiciones a partir del offset de la pagina")
    void toRankingEntries_UsesOffsetForPositions() {
        Community community = community(player(1L, "Ana", 1700));
        List<CommunityMembership> memberships = List.of(
                CommunityMembership.builder().community(community).player(player(1L, "Ana", 1700)).build(),
                CommunityMembership.builder().community(community).player(player(2L, "Luis", 1650)).build());

        List<CommunityRankingEntryDTO> entries = mapper.toRankingEntries(memberships, 20);

        assertThat(entries).extracting(CommunityRankingEntryDTO::getPosition).containsExactly(21, 22);
        assertThat(entries.get(1).getPlayerName()).isEqualTo("Luis");
        assertThat(entries.get(1).getRatingDeviation()).isEqualTo(120.0);
    }

    @Test
    @DisplayName("toDetailDto: isMember depende de que exista un rol del solicitante")
    void toDetailDto_IsMemberDependsOnRole() {
        Community community = community(player(1L, "Ana", 1700));

        CommunityDetailResponseDTO asMember = mapper.toDetailDto(community, CommunityRole.MEMBER, 3L);
        CommunityDetailResponseDTO asVisitor = mapper.toDetailDto(community, null, 3L);

        assertThat(asMember.getIsMember()).isTrue();
        assertThat(asVisitor.getIsMember()).isFalse();
        assertThat(asVisitor.getCreatorName()).isEqualTo("Ana");
        assertThat(asVisitor.getMemberCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("toMemberDto: expone rol, estado y rating del jugador")
    void toMemberDto_MapsMembershipFields() {
        CommunityMembership membership = CommunityMembership.builder()
                .id(5L)
                .player(player(2L, "Luis", 1650))
                .role(CommunityRole.MEMBER)
                .status(MembershipStatus.ACTIVE)
                .build();

        CommunityMemberResponseDTO dto = mapper.toMemberDto(membership);

        assertThat(dto.getMembershipId()).isEqualTo(5L);
        assertThat(dto.getPlayerId()).isEqualTo(2L);
        assertThat(dto.getRatingGlicko()).isEqualTo(1650.0);
        assertThat(dto.getRole()).isEqualTo(CommunityRole.MEMBER);
    }
}

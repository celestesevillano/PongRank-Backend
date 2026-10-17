package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.Community.Community;
import org.example.pongrankbackend.Community.dto.CommunityDetailResponseDTO;
import org.example.pongrankbackend.Community.dto.CommunityResponseDTO;
import org.example.pongrankbackend.CommunityMembership.CommunityMembership;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberResponseDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityRankingEntryDTO;
import org.example.pongrankbackend.Player.Player;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CommunityMapper {

    private final ModelMapper modelMapper;

    public CommunityMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public CommunityResponseDTO toCommunityDto(Community community, CommunityRole myRole,
                                               long memberCount) {
        CommunityResponseDTO dto = modelMapper.map(community, CommunityResponseDTO.class);
        dto.setMyRole(myRole);
        dto.setMemberCount(memberCount);
        return dto;
    }

    public CommunityDetailResponseDTO toDetailDto(Community community, CommunityRole myRole,
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

    public CommunityMemberResponseDTO toMemberDto(CommunityMembership membership) {
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

    public List<CommunityRankingEntryDTO> toRankingEntries(List<CommunityMembership> memberships,
                                                           int offset) {
        List<CommunityRankingEntryDTO> entries = new ArrayList<>();

        for (int i = 0; i < memberships.size(); i++) {
            entries.add(toRankingDto(memberships.get(i), offset + i + 1));
        }

        return entries;
    }

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
}

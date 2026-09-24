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

public interface CommunityService {

    CommunityResponseDTO createCommunity(CommunityCreateRequestDTO dto, Long requesterId);

    Page<CommunityResponseDTO> searchCommunities(String name, CommunityType type,
                                                 Pageable pageable, Long requesterId);

    List<CommunityResponseDTO> getMyCommunities(Long requesterId);

    CommunityDetailResponseDTO getCommunityById(Long communityId, Long requesterId);

    CommunityResponseDTO updateCommunity(Long communityId, CommunityUpdateRequestDTO dto, Long requesterId);

    void archiveCommunity(Long communityId, Long requesterId);

    Page<CommunityMemberResponseDTO> getCommunityMembers(Long communityId, Pageable pageable, Long requesterId);

    Page<CommunityRankingEntryDTO> getCommunityRanking(Long communityId, Pageable pageable, Long requesterId);

    CommunityMemberResponseDTO addMember(Long communityId, CommunityMemberAddRequestDTO dto, Long requesterId);


    CommunityMemberResponseDTO updateMemberRole(Long communityId, Long playerId,
                                                CommunityMemberRoleUpdateDTO dto, Long requesterId);

    void removeMember(Long communityId, Long playerId, Long requesterId);
}
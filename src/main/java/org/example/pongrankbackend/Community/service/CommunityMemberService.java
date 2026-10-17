package org.example.pongrankbackend.Community.service;

import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberAddRequestDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberResponseDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityMemberRoleUpdateDTO;
import org.example.pongrankbackend.CommunityMembership.dto.CommunityRankingEntryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommunityMemberService {

    Page<CommunityMemberResponseDTO> getCommunityMembers(Long communityId, Pageable pageable, Long requesterId);

    Page<CommunityRankingEntryDTO> getCommunityRanking(Long communityId, Pageable pageable, Long requesterId);

    CommunityMemberResponseDTO addMember(Long communityId, CommunityMemberAddRequestDTO dto, Long requesterId);

    CommunityMemberResponseDTO updateMemberRole(Long communityId, Long playerId,
                                                CommunityMemberRoleUpdateDTO dto, Long requesterId);

    void removeMember(Long communityId, Long playerId, Long requesterId);
}

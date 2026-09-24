package org.example.pongrankbackend.ClubMembership.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.ClubMembership.dto.ClubMembershipRequestDTO;
import org.example.pongrankbackend.ClubMembership.dto.ClubMembershipResponseDTO;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;


public interface ClubMembershipService {

    ClubMembershipResponseDTO requestMembership(Long actingPlayerId, ClubMembershipRequestDTO dto);

    PageResponseDTO<ClubMembershipResponseDTO> getPendingRequests(Long clubId, Long actingPlayerId, int page, int size);

    ClubMembershipResponseDTO approveRequest(Long membershipId, Long actingPlayerId);

    ClubMembershipResponseDTO rejectRequest(Long membershipId, Long actingPlayerId);

    ClubMembershipResponseDTO cancelRequest(Long membershipId, Long actingPlayerId);

    ClubMembershipResponseDTO leaveClub(Long membershipId, Long actingPlayerId);

    PageResponseDTO<ClubMembershipResponseDTO> getActiveMembers(Long clubId, int page, int size);

    PageResponseDTO<ClubMembershipResponseDTO> getPlayerMembershipHistory(Long playerId, int page, int size);

    // Internal operations used by ClubService (not exposed through HTTP)

    boolean hasActiveMembershipOutsideClub(Long playerId, Long clubId);

    boolean isActiveMember(Long clubId, Long playerId);

    void addAdminAsMember(Club club);

    Player transferAdminRole(Club club, Long newAdminPlayerId);
}

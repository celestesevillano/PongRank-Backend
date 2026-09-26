package org.example.pongrankbackend.ClubMembership.service;

import org.example.pongrankbackend.Club.Club;
import org.example.pongrankbackend.ClubMembership.dto.ClubMembershipRequestDTO;
import org.example.pongrankbackend.ClubMembership.dto.ClubMembershipResponseDTO;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.common.pagination.PageResponseDTO;


// El jugador que actúa se lee del SecurityContext (SecurityUtils), no se recibe como parámetro,
// salvo en las operaciones internas usadas por ClubService donde el playerId es un tercero (ej. el nuevo admin).
public interface ClubMembershipService {

    ClubMembershipResponseDTO requestMembership(ClubMembershipRequestDTO dto);

    PageResponseDTO<ClubMembershipResponseDTO> getPendingRequests(Long clubId, int page, int size);

    ClubMembershipResponseDTO approveRequest(Long membershipId);

    ClubMembershipResponseDTO rejectRequest(Long membershipId);

    ClubMembershipResponseDTO cancelRequest(Long membershipId);

    ClubMembershipResponseDTO leaveClub(Long membershipId);

    PageResponseDTO<ClubMembershipResponseDTO> getActiveMembers(Long clubId, int page, int size);

    PageResponseDTO<ClubMembershipResponseDTO> getPlayerMembershipHistory(Long playerId, int page, int size);

    // Internal operations used by ClubService (not exposed through HTTP)

    // Total de membresías activas (PENDING+APPROVED) del jugador, en cualquier club
    long countActiveMemberships(Long playerId);

    boolean isActiveMember(Long clubId, Long playerId);

    void addAdminAsMember(Club club);

    Player transferAdminRole(Club club, Long newAdminPlayerId);
}

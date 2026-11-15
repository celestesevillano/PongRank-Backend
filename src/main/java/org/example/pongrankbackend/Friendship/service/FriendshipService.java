package org.example.pongrankbackend.Friendship.service;

import org.example.pongrankbackend.Friendship.dto.FriendshipRequestDTO;
import org.example.pongrankbackend.Friendship.dto.FriendshipResponseDTO;
import org.example.pongrankbackend.Friendship.dto.PongmigosProfileDTO;
import org.example.pongrankbackend.Friendship.dto.PongmigosVisibilityDTO;
import org.example.pongrankbackend.Player.PongmigosVisibility;

import java.util.List;

public interface FriendshipService {

    FriendshipResponseDTO sendFriendRequest(Long senderId, FriendshipRequestDTO dto);

    FriendshipResponseDTO sendFriendRequest(Long requestedSenderId, Long actingPlayerId, FriendshipRequestDTO dto);

    FriendshipResponseDTO acceptFriendRequest(Long friendshipId, Long actingPlayerId);

    FriendshipResponseDTO rejectFriendRequest(Long friendshipId, Long actingPlayerId);

    void cancelFriendRequest(Long friendshipId, Long actingPlayerId);

    void unfriend(Long friendshipId, Long actingPlayerId);

    List<FriendshipResponseDTO> getAcceptedFriends(Long playerId);

    List<FriendshipResponseDTO> getPendingRequests(Long playerId);

    // Solicitudes que el jugador envió y siguen sin respuesta
    List<FriendshipResponseDTO> getSentRequests(Long playerId);

    PongmigosVisibilityDTO getPongmigosVisibility(Long playerId);

    PongmigosVisibilityDTO updatePongmigosVisibility(Long playerId, PongmigosVisibility visibility);

    PongmigosProfileDTO getPongmigosProfile(Long playerId, Long viewerId, int page, int size);
}

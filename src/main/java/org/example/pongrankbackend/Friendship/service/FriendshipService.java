package org.example.pongrankbackend.Friendship.service;

import org.example.pongrankbackend.Friendship.dto.FriendshipRequestDTO;
import org.example.pongrankbackend.Friendship.dto.FriendshipResponseDTO;

import java.util.List;

public interface FriendshipService {

    FriendshipResponseDTO sendFriendRequest(Long senderId, FriendshipRequestDTO dto);

    FriendshipResponseDTO acceptFriendRequest(Long friendshipId, Long actingPlayerId);

    FriendshipResponseDTO rejectFriendRequest(Long friendshipId, Long actingPlayerId);

    List<FriendshipResponseDTO> getAcceptedFriends(Long playerId);

    List<FriendshipResponseDTO> getPendingRequests(Long playerId);
}

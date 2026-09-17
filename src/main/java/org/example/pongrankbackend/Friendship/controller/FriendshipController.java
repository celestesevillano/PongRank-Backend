package org.example.pongrankbackend.Friendship.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Friendship.dto.FriendshipRequestDTO;
import org.example.pongrankbackend.Friendship.dto.FriendshipResponseDTO;
import org.example.pongrankbackend.Friendship.service.FriendshipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/friendships")
public class FriendshipController {

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    // TODO: Extract senderId from authenticated user via SecurityContext instead of client path parameter
    @PostMapping("/players/{senderId}/requests")
    public ResponseEntity<FriendshipResponseDTO> sendFriendRequest(
            @PathVariable Long senderId,
            @Valid @RequestBody FriendshipRequestDTO dto) {
        FriendshipResponseDTO response = friendshipService.sendFriendRequest(senderId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext
    @PatchMapping("/{friendshipId}/accept")
    public ResponseEntity<FriendshipResponseDTO> acceptFriendRequest(
            @PathVariable Long friendshipId,
            @RequestParam Long actingPlayerId) {
        FriendshipResponseDTO response = friendshipService.acceptFriendRequest(friendshipId, actingPlayerId);
        return ResponseEntity.ok(response);
    }

    // TODO: Replace actingPlayerId @RequestParam with authenticated user from SecurityContext
    @PatchMapping("/{friendshipId}/reject")
    public ResponseEntity<FriendshipResponseDTO> rejectFriendRequest(
            @PathVariable Long friendshipId,
            @RequestParam Long actingPlayerId) {
        FriendshipResponseDTO response = friendshipService.rejectFriendRequest(friendshipId, actingPlayerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/players/{playerId}")
    public ResponseEntity<List<FriendshipResponseDTO>> getAcceptedFriends(@PathVariable Long playerId) {
        List<FriendshipResponseDTO> response = friendshipService.getAcceptedFriends(playerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/players/{playerId}/pending")
    public ResponseEntity<List<FriendshipResponseDTO>> getPendingRequests(@PathVariable Long playerId) {
        List<FriendshipResponseDTO> response = friendshipService.getPendingRequests(playerId);
        return ResponseEntity.ok(response);
    }
}

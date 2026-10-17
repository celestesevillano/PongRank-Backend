package org.example.pongrankbackend.Friendship.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Friendship.dto.FriendshipRequestDTO;
import org.example.pongrankbackend.Friendship.dto.FriendshipResponseDTO;
import org.example.pongrankbackend.Friendship.service.FriendshipService;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/friendships")
public class FriendshipController {

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @PostMapping({"/players/{senderId}/requests", "/requests"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FriendshipResponseDTO> sendFriendRequest(
            @PathVariable(required = false) Long senderId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody FriendshipRequestDTO dto) {
        FriendshipResponseDTO response = friendshipService.sendFriendRequest(senderId, currentUser.getId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{friendshipId}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FriendshipResponseDTO> acceptFriendRequest(
            @PathVariable Long friendshipId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        FriendshipResponseDTO response = friendshipService.acceptFriendRequest(friendshipId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{friendshipId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FriendshipResponseDTO> rejectFriendRequest(
            @PathVariable Long friendshipId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        FriendshipResponseDTO response = friendshipService.rejectFriendRequest(friendshipId, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{friendshipId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelFriendRequest(
            @PathVariable Long friendshipId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        friendshipService.cancelFriendRequest(friendshipId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{friendshipId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unfriend(
            @PathVariable Long friendshipId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        friendshipService.unfriend(friendshipId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/players/{playerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FriendshipResponseDTO>> getAcceptedFriends(
            @PathVariable Long playerId) {
        List<FriendshipResponseDTO> response = friendshipService.getAcceptedFriends(playerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/players/{playerId}/pending")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FriendshipResponseDTO>> getPendingRequests(
            @PathVariable Long playerId) {
        List<FriendshipResponseDTO> response = friendshipService.getPendingRequests(playerId);
        return ResponseEntity.ok(response);
    }
}

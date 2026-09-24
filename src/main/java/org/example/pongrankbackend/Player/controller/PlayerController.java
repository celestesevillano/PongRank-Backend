package org.example.pongrankbackend.Player.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.dto.PlayerUpdateRequestDTO;
import org.example.pongrankbackend.Player.service.PlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping("/register")
    public ResponseEntity<PlayerResponseDTO> registerPlayer(@Valid @RequestBody PlayerRegisterRequestDTO dto) {
        PlayerResponseDTO response = playerService.registerPlayer(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{playerId}")
    public ResponseEntity<PlayerResponseDTO> getPlayerById(@PathVariable Long playerId) {
        PlayerResponseDTO response = playerService.getPlayerById(playerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{playerId}/summary")
    public ResponseEntity<PlayerSummaryDTO> getPlayerSummaryById(@PathVariable Long playerId) {
        PlayerSummaryDTO response = playerService.getPlayerSummaryById(playerId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{playerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PlayerResponseDTO> updatePlayer(
            @PathVariable Long playerId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody PlayerUpdateRequestDTO dto) {
        if (!currentUser.getId().equals(playerId) && !currentUser.getRole().equals("ROLE_SYSTEM_ADMIN")) {
            throw new UnauthorizedActionException("Solo puedes actualizar la información de tu propio perfil");
        }
        PlayerResponseDTO response = playerService.updatePlayer(playerId, dto);
        return ResponseEntity.ok(response);
    }
}

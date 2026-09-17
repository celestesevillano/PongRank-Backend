package org.example.pongrankbackend.Player.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.dto.PlayerUpdateRequestDTO;
import org.example.pongrankbackend.Player.service.PlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    // TODO: Endpoint may be moved to AuthController when full authentication module is integrated
    @PostMapping("/register")
    public ResponseEntity<PlayerResponseDTO> registerPlayer(@Valid @RequestBody PlayerRegisterRequestDTO dto) {
        PlayerResponseDTO response = playerService.registerPlayer(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // TODO: Protect via SecurityContext so users cannot arbitrarily query another user's private profile
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

    // TODO: Validate playerId against authenticated user via SecurityContext
    @PatchMapping("/{playerId}")
    public ResponseEntity<PlayerResponseDTO> updatePlayer(
            @PathVariable Long playerId,
            @Valid @RequestBody PlayerUpdateRequestDTO dto) {
        PlayerResponseDTO response = playerService.updatePlayer(playerId, dto);
        return ResponseEntity.ok(response);
    }
}

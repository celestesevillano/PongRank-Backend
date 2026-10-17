package org.example.pongrankbackend.TrainingSession.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionCreateDTO;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionResponseDTO;
import org.example.pongrankbackend.TrainingSession.service.TrainingSessionService;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/training-sessions")
@PreAuthorize("isAuthenticated()")
public class TrainingSessionController {

    private final TrainingSessionService trainingSessionService;

    public TrainingSessionController(TrainingSessionService trainingSessionService) {
        this.trainingSessionService = trainingSessionService;
    }

    @PostMapping({"/players/{playerId}", ""})
    public ResponseEntity<TrainingSessionResponseDTO> submit(
            @PathVariable(required = false) Long playerId,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody TrainingSessionCreateDTO dto) {
        TrainingSessionResponseDTO response = trainingSessionService.registerSession(playerId, currentUser.getId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Historial de sesiones de entrenamiento con paginación y orden cronológico descendente.
     */
    @GetMapping("/players/{playerId}")
    public ResponseEntity<Page<TrainingSessionResponseDTO>> history(
            @PathVariable Long playerId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TrainingSessionResponseDTO> response = trainingSessionService.getHistoryByPlayer(playerId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Consulta la mejor sesión histórica (récord personal de postura) del jugador.
     */
    @GetMapping("/players/{playerId}/best")
    public ResponseEntity<TrainingSessionResponseDTO> getBestSession(
            @PathVariable Long playerId) {
        TrainingSessionResponseDTO response = trainingSessionService.getBestSessionByPlayer(playerId);
        return ResponseEntity.ok(response);
    }
}

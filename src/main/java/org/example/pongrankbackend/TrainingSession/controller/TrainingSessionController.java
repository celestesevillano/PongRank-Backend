package org.example.pongrankbackend.TrainingSession.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionCreateDTO;
import org.example.pongrankbackend.TrainingSession.dto.TrainingSessionResponseDTO;
import org.example.pongrankbackend.TrainingSession.service.TrainingSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/training-sessions")
public class TrainingSessionController {

    private final TrainingSessionService trainingSessionService;

    public TrainingSessionController(TrainingSessionService trainingSessionService) {
        this.trainingSessionService = trainingSessionService;
    }

    // TODO: Extract playerId from authenticated user via SecurityContext instead of path parameter
    @PostMapping("/players/{playerId}")
    public ResponseEntity<TrainingSessionResponseDTO> submit(
            @PathVariable Long playerId,
            @Valid @RequestBody TrainingSessionCreateDTO dto) {
        TrainingSessionResponseDTO response = trainingSessionService.registerSession(playerId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/players/{playerId}")
    public ResponseEntity<List<TrainingSessionResponseDTO>> history(@PathVariable Long playerId) {
        List<TrainingSessionResponseDTO> response = trainingSessionService.getHistoryByPlayer(playerId);
        return ResponseEntity.ok(response);
    }
}

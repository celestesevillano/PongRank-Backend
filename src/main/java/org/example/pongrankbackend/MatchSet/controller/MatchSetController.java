package org.example.pongrankbackend.MatchSet.controller;

import org.example.pongrankbackend.MatchSet.dto.MatchSetResponseDTO;
import org.example.pongrankbackend.MatchSet.service.MatchSetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MatchSetController {

    private final MatchSetService matchSetService;

    public MatchSetController(MatchSetService matchSetService) {
        this.matchSetService = matchSetService;
    }

    /**
     * Obtiene la lista ordenada de sets disputados en un partido específico.
     */
    @GetMapping("/matches/{matchId}/sets")
    public ResponseEntity<List<MatchSetResponseDTO>> getSetsByMatchId(@PathVariable Long matchId) {
        List<MatchSetResponseDTO> response = matchSetService.getSetsByMatchId(matchId);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene el detalle de un set individual por su ID único.
     */
    @GetMapping("/match-sets/{setId}")
    public ResponseEntity<MatchSetResponseDTO> getSetById(@PathVariable Long setId) {
        MatchSetResponseDTO response = matchSetService.getSetById(setId);
        return ResponseEntity.ok(response);
    }
}

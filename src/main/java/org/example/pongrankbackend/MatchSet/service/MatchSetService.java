package org.example.pongrankbackend.MatchSet.service;

import org.example.pongrankbackend.MatchSet.dto.MatchSetResponseDTO;

import java.util.List;

public interface MatchSetService {

    List<MatchSetResponseDTO> getSetsByMatchId(Long matchId);

    MatchSetResponseDTO getSetById(Long setId);
}

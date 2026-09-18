package org.example.pongrankbackend.Player.service;

import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.dto.PlayerUpdateRequestDTO;

public interface PlayerService {

    PlayerResponseDTO registerPlayer(PlayerRegisterRequestDTO dto);

    PlayerResponseDTO getPlayerById(Long id);

    PlayerSummaryDTO getPlayerSummaryById(Long id);

    PlayerResponseDTO updatePlayer(Long playerId, PlayerUpdateRequestDTO dto);
}

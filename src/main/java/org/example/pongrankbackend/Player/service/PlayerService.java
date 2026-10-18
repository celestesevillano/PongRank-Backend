package org.example.pongrankbackend.Player.service;

import org.example.pongrankbackend.Player.dto.DeleteAccountRequestDTO;
import org.example.pongrankbackend.Player.dto.InitialLevelResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.dto.PlayerUpdateRequestDTO;

import java.util.List;

public interface PlayerService {

    PlayerResponseDTO registerPlayer(PlayerRegisterRequestDTO dto);

    PlayerResponseDTO getPlayerById(Long id);

    PlayerSummaryDTO getPlayerSummaryById(Long id);

    PlayerResponseDTO updatePlayer(Long playerId, PlayerUpdateRequestDTO dto);

    // Elimina (soft-delete) la cuenta del jugador autenticado actualmente
    void deleteAccount(DeleteAccountRequestDTO dto);

    List<InitialLevelResponseDTO> getInitialLevels();
}

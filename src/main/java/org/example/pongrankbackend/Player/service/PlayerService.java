package org.example.pongrankbackend.Player.service;

import org.example.pongrankbackend.Player.dto.CategorySuggestionRequestDTO;
import org.example.pongrankbackend.Player.dto.CategorySuggestionResponseDTO;
import org.example.pongrankbackend.Player.dto.DeleteAccountRequestDTO;
import org.example.pongrankbackend.Player.dto.InitialLevelResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Player.dto.PlayerUpdateRequestDTO;

import org.example.pongrankbackend.common.pagination.PageResponseDTO;

import java.util.List;

public interface PlayerService {

    PlayerResponseDTO registerPlayer(PlayerRegisterRequestDTO dto);

    PlayerResponseDTO getPlayerById(Long id);

    PlayerSummaryDTO getPlayerSummaryById(Long id);

    // Busca jugadores activos por nombre o usuario (sin incluir al jugador autenticado)
    PageResponseDTO<PlayerSummaryDTO> searchPlayers(String name, int page, int size);

    PlayerResponseDTO updatePlayer(Long playerId, PlayerUpdateRequestDTO dto);

    // Elimina (soft-delete) la cuenta del jugador autenticado actualmente
    void deleteAccount(DeleteAccountRequestDTO dto);

    List<InitialLevelResponseDTO> getInitialLevels();

    CategorySuggestionResponseDTO suggestCategory(CategorySuggestionRequestDTO dto);
}

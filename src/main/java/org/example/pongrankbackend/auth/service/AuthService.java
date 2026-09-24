package org.example.pongrankbackend.auth.service;

import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.auth.dto.AuthRequestDTO;
import org.example.pongrankbackend.auth.dto.AuthResponseDTO;
import org.example.pongrankbackend.auth.dto.RefreshTokenRequestDTO;

public interface AuthService {

    AuthResponseDTO register(PlayerRegisterRequestDTO dto);

    AuthResponseDTO login(AuthRequestDTO dto);

    AuthResponseDTO refreshToken(RefreshTokenRequestDTO dto);

    PlayerResponseDTO getCurrentUserProfile();
}

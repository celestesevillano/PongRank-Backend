package org.example.pongrankbackend.auth.service;

import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.auth.dto.AuthRequestDTO;
import org.example.pongrankbackend.auth.dto.AuthResponseDTO;
import org.example.pongrankbackend.auth.dto.ForgotPasswordRequestDTO;
import org.example.pongrankbackend.auth.dto.RefreshTokenRequestDTO;
import org.example.pongrankbackend.auth.dto.ResetPasswordRequestDTO;

public interface AuthService {

    AuthResponseDTO register(PlayerRegisterRequestDTO dto);

    AuthResponseDTO login(AuthRequestDTO dto);

    AuthResponseDTO refreshToken(RefreshTokenRequestDTO dto);

    PlayerResponseDTO getCurrentUserProfile();

    // Siempre responde igual exista o no el email, para no filtrar qué correos están registrados
    void forgotPassword(ForgotPasswordRequestDTO dto);

    void resetPassword(ResetPasswordRequestDTO dto);
}

package org.example.pongrankbackend.auth.service;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.auth.PasswordResetToken;
import org.example.pongrankbackend.auth.dto.AuthRequestDTO;
import org.example.pongrankbackend.auth.dto.AuthResponseDTO;
import org.example.pongrankbackend.auth.dto.ForgotPasswordRequestDTO;
import org.example.pongrankbackend.auth.dto.RefreshTokenRequestDTO;
import org.example.pongrankbackend.auth.dto.ResetPasswordRequestDTO;
import org.example.pongrankbackend.auth.repository.PasswordResetTokenRepository;
import org.example.pongrankbackend.common.exception.EmailAlreadyExistsException;
import org.example.pongrankbackend.common.exception.InvalidCredentialsException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.example.pongrankbackend.email.service.EmailService;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.example.pongrankbackend.security.JwtService;
import org.example.pongrankbackend.security.SecurityUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final int RESET_TOKEN_EXPIRATION_MINUTES = 30;

    private final PlayerRepository playerRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final ModelMapper modelMapper;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public AuthServiceImpl(PlayerRepository playerRepository,
                           PasswordResetTokenRepository passwordResetTokenRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           EmailService emailService,
                           ModelMapper modelMapper) {
        this.playerRepository = playerRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.modelMapper = modelMapper;
    }

    @Override
    @Transactional
    public AuthResponseDTO register(PlayerRegisterRequestDTO dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();

        if (playerRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("El email '" + normalizedEmail + "' ya se encuentra registrado");
        }

        Player player = modelMapper.map(dto, Player.class);
        player.setEmail(normalizedEmail);
        player.setPassword(passwordEncoder.encode(dto.getPassword()));
        player.setRole(Role.ROLE_USER);
        player.setStatus(PlayerStatus.ACTIVE);

        if (player.getShareContact() == null) {
            player.setShareContact(false);
        }
        if (player.getFederatedDeclared() == null) {
            player.setFederatedDeclared(false);
        }

        Player savedPlayer = playerRepository.save(player);
        emailService.sendWelcomeEmail(savedPlayer);

        CustomUserDetails userDetails = new CustomUserDetails(savedPlayer);

        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        return AuthResponseDTO.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .player(modelMapper.map(savedPlayer, PlayerResponseDTO.class))
                .build();
    }

    @Override
    public AuthResponseDTO login(AuthRequestDTO dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();

        Player player = playerRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales inválidas: correo electrónico o contraseña incorrectos"));

        if (!passwordEncoder.matches(dto.getPassword(), player.getPassword())) {
            throw new InvalidCredentialsException("Credenciales inválidas: correo electrónico o contraseña incorrectos");
        }

        if (player.getStatus() != PlayerStatus.ACTIVE) {
            throw new InvalidCredentialsException("La cuenta del jugador no se encuentra activa o está suspendida");
        }

        CustomUserDetails userDetails = new CustomUserDetails(player);
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        return AuthResponseDTO.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .player(modelMapper.map(player, PlayerResponseDTO.class))
                .build();
    }

    @Override
    public AuthResponseDTO refreshToken(RefreshTokenRequestDTO dto) {
        String email;
        try {
            email = jwtService.extractEmail(dto.getRefreshToken());
        } catch (Exception e) {
            throw new InvalidCredentialsException("El refresh token proporcionado es inválido o no se pudo procesar");
        }

        if (email == null) {
            throw new InvalidCredentialsException("El refresh token no contiene un identificador de usuario válido");
        }

        Player player = playerRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Usuario no encontrado para el token proporcionado"));

        if (player.getStatus() != PlayerStatus.ACTIVE) {
            throw new InvalidCredentialsException("La cuenta del jugador no se encuentra activa o está suspendida");
        }

        CustomUserDetails userDetails = new CustomUserDetails(player);

        if (!jwtService.isRefreshTokenValid(dto.getRefreshToken(), userDetails)) {
            throw new InvalidCredentialsException("El refresh token ha expirado o es inválido");
        }

        String newAccessToken = jwtService.generateAccessToken(userDetails);

        return AuthResponseDTO.builder()
                .token(newAccessToken)
                .refreshToken(dto.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .player(modelMapper.map(player, PlayerResponseDTO.class))
                .build();
    }

    @Override
    public PlayerResponseDTO getCurrentUserProfile() {
        Long currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedActionException("No hay una sesión de usuario autenticada"));

        Player player = playerRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + currentUserId));

        return modelMapper.map(player, PlayerResponseDTO.class);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequestDTO dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();

        playerRepository.findByEmail(normalizedEmail).ifPresent(player -> {
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(UUID.randomUUID().toString())
                    .player(player)
                    .expiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRATION_MINUTES))
                    .build();
            passwordResetTokenRepository.save(resetToken);

            String resetLink = frontendUrl + "/reset-password?token=" + resetToken.getToken();
            emailService.sendPasswordResetEmail(player, resetLink, RESET_TOKEN_EXPIRATION_MINUTES);
        });

        // No se informa si el email existe o no, para no filtrar cuentas registradas
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDTO dto) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(dto.getToken())
                .orElseThrow(() -> new InvalidCredentialsException("El enlace de restablecimiento es inválido"));

        if (resetToken.isUsed() || resetToken.isExpired()) {
            throw new InvalidCredentialsException("El enlace de restablecimiento ha vencido o ya fue utilizado");
        }

        Player player = resetToken.getPlayer();
        player.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        playerRepository.save(player);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }
}

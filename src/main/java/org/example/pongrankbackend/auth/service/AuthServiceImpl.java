package org.example.pongrankbackend.auth.service;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.auth.dto.AuthRequestDTO;
import org.example.pongrankbackend.auth.dto.AuthResponseDTO;
import org.example.pongrankbackend.auth.dto.RefreshTokenRequestDTO;
import org.example.pongrankbackend.common.exception.EmailAlreadyExistsException;
import org.example.pongrankbackend.common.exception.InvalidCredentialsException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.example.pongrankbackend.security.JwtService;
import org.example.pongrankbackend.security.SecurityUtils;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ModelMapper modelMapper;

    public AuthServiceImpl(PlayerRepository playerRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           ModelMapper modelMapper) {
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
}

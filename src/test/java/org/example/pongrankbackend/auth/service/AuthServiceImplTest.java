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
import org.example.pongrankbackend.security.CustomUserDetails;
import org.example.pongrankbackend.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    private Player createPlayer(Long id, String email, PlayerStatus status) {
        return Player.builder()
                .id(id)
                .name("Adriana")
                .email(email)
                .password("encoded_secret_pass")
                .role(Role.ROLE_USER)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("shouldRegisterNewPlayerAndReturnTokensWhenValidRequest")
    void shouldRegisterNewPlayerAndReturnTokensWhenValidRequest() {
        // Arrange
        PlayerRegisterRequestDTO dto = PlayerRegisterRequestDTO.builder()
                .name("Adriana")
                .email("adriana@utec.edu.pe")
                .password("Password123")
                .build();

        Player mappedPlayer = Player.builder().name("Adriana").email("adriana@utec.edu.pe").build();
        Player savedPlayer = createPlayer(1L, "adriana@utec.edu.pe", PlayerStatus.ACTIVE);
        PlayerResponseDTO responseDto = PlayerResponseDTO.builder().id(1L).email("adriana@utec.edu.pe").name("Adriana").build();

        when(playerRepository.existsByEmail("adriana@utec.edu.pe")).thenReturn(false);
        when(modelMapper.map(dto, Player.class)).thenReturn(mappedPlayer);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded_secret_pass");
        when(playerRepository.save(any(Player.class))).thenReturn(savedPlayer);
        when(jwtService.generateAccessToken(any(CustomUserDetails.class))).thenReturn("access_token_xyz");
        when(jwtService.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("refresh_token_xyz");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);
        when(modelMapper.map(savedPlayer, PlayerResponseDTO.class)).thenReturn(responseDto);

        // Act
        AuthResponseDTO result = authService.register(dto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isEqualTo("access_token_xyz");
        assertThat(result.getRefreshToken()).isEqualTo("refresh_token_xyz");
        assertThat(result.getTokenType()).isEqualTo("Bearer");
        assertThat(result.getExpiresIn()).isEqualTo(86400000L);
        assertThat(result.getPlayer().getEmail()).isEqualTo("adriana@utec.edu.pe");

        verify(playerRepository).save(any(Player.class));
    }

    @Test
    @DisplayName("shouldThrowEmailAlreadyExistsExceptionWhenEmailIsRegistered")
    void shouldThrowEmailAlreadyExistsExceptionWhenEmailIsRegistered() {
        // Arrange
        PlayerRegisterRequestDTO dto = PlayerRegisterRequestDTO.builder()
                .name("Adriana")
                .email("existente@utec.edu.pe")
                .password("Password123")
                .build();

        when(playerRepository.existsByEmail("existente@utec.edu.pe")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.register(dto))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("ya se encuentra registrado");

        verify(playerRepository, never()).save(any());
    }

    @Test
    @DisplayName("shouldLoginSuccessfullyAndReturnTokensWhenCredentialsAreValid")
    void shouldLoginSuccessfullyAndReturnTokensWhenCredentialsAreValid() {
        // Arrange
        AuthRequestDTO dto = AuthRequestDTO.builder()
                .email("adriana@utec.edu.pe")
                .password("Password123")
                .build();

        Player player = createPlayer(1L, "adriana@utec.edu.pe", PlayerStatus.ACTIVE);
        PlayerResponseDTO playerResponse = PlayerResponseDTO.builder().id(1L).email("adriana@utec.edu.pe").build();

        when(playerRepository.findByEmail("adriana@utec.edu.pe")).thenReturn(Optional.of(player));
        when(passwordEncoder.matches("Password123", "encoded_secret_pass")).thenReturn(true);
        when(jwtService.generateAccessToken(any(CustomUserDetails.class))).thenReturn("access_token_xyz");
        when(jwtService.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("refresh_token_xyz");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);
        when(modelMapper.map(player, PlayerResponseDTO.class)).thenReturn(playerResponse);

        // Act
        AuthResponseDTO result = authService.login(dto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isEqualTo("access_token_xyz");
        assertThat(result.getRefreshToken()).isEqualTo("refresh_token_xyz");
        assertThat(result.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("shouldThrowInvalidCredentialsExceptionWhenPasswordIsIncorrect")
    void shouldThrowInvalidCredentialsExceptionWhenPasswordIsIncorrect() {
        // Arrange
        AuthRequestDTO dto = AuthRequestDTO.builder()
                .email("adriana@utec.edu.pe")
                .password("WrongPassword")
                .build();

        Player player = createPlayer(1L, "adriana@utec.edu.pe", PlayerStatus.ACTIVE);

        when(playerRepository.findByEmail("adriana@utec.edu.pe")).thenReturn(Optional.of(player));
        when(passwordEncoder.matches("WrongPassword", "encoded_secret_pass")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Credenciales inválidas");
    }

    @Test
    @DisplayName("shouldThrowInvalidCredentialsExceptionWhenEmailNotFound")
    void shouldThrowInvalidCredentialsExceptionWhenEmailNotFound() {
        // Arrange
        AuthRequestDTO dto = AuthRequestDTO.builder()
                .email("desconocido@utec.edu.pe")
                .password("Password123")
                .build();

        when(playerRepository.findByEmail("desconocido@utec.edu.pe")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Credenciales inválidas");
    }

    @Test
    @DisplayName("shouldThrowInvalidCredentialsExceptionWhenAccountIsSuspended")
    void shouldThrowInvalidCredentialsExceptionWhenAccountIsSuspended() {
        // Arrange
        AuthRequestDTO dto = AuthRequestDTO.builder()
                .email("suspendido@utec.edu.pe")
                .password("Password123")
                .build();

        Player suspendedPlayer = createPlayer(2L, "suspendido@utec.edu.pe", PlayerStatus.SUSPENDED);

        when(playerRepository.findByEmail("suspendido@utec.edu.pe")).thenReturn(Optional.of(suspendedPlayer));
        when(passwordEncoder.matches("Password123", "encoded_secret_pass")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("suspendida");
    }

    @Test
    @DisplayName("shouldRefreshTokenSuccessfullyWhenRefreshTokenIsValid")
    void shouldRefreshTokenSuccessfullyWhenRefreshTokenIsValid() {
        // Arrange
        RefreshTokenRequestDTO dto = RefreshTokenRequestDTO.builder()
                .refreshToken("valid_refresh_token")
                .build();

        Player player = createPlayer(1L, "adriana@utec.edu.pe", PlayerStatus.ACTIVE);
        PlayerResponseDTO playerResponse = PlayerResponseDTO.builder().id(1L).email("adriana@utec.edu.pe").build();

        when(jwtService.extractEmail("valid_refresh_token")).thenReturn("adriana@utec.edu.pe");
        when(playerRepository.findByEmail("adriana@utec.edu.pe")).thenReturn(Optional.of(player));
        when(jwtService.isRefreshTokenValid(eq("valid_refresh_token"), any(CustomUserDetails.class))).thenReturn(true);
        when(jwtService.generateAccessToken(any(CustomUserDetails.class))).thenReturn("brand_new_access_token");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);
        when(modelMapper.map(player, PlayerResponseDTO.class)).thenReturn(playerResponse);

        // Act
        AuthResponseDTO result = authService.refreshToken(dto);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isEqualTo("brand_new_access_token");
        assertThat(result.getRefreshToken()).isEqualTo("valid_refresh_token");
    }

    @Test
    @DisplayName("shouldThrowInvalidCredentialsExceptionWhenRefreshTokenIsInvalid")
    void shouldThrowInvalidCredentialsExceptionWhenRefreshTokenIsInvalid() {
        // Arrange
        RefreshTokenRequestDTO dto = RefreshTokenRequestDTO.builder()
                .refreshToken("expired_or_invalid_refresh_token")
                .build();

        Player player = createPlayer(1L, "adriana@utec.edu.pe", PlayerStatus.ACTIVE);

        when(jwtService.extractEmail("expired_or_invalid_refresh_token")).thenReturn("adriana@utec.edu.pe");
        when(playerRepository.findByEmail("adriana@utec.edu.pe")).thenReturn(Optional.of(player));
        when(jwtService.isRefreshTokenValid(eq("expired_or_invalid_refresh_token"), any(CustomUserDetails.class))).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.refreshToken(dto))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("ha expirado o es inválido");
    }

    @Test
    @DisplayName("shouldThrowInvalidCredentialsExceptionWhenAccountIsSuspendedOnRefreshToken")
    void shouldThrowInvalidCredentialsExceptionWhenAccountIsSuspendedOnRefreshToken() {
        // Arrange
        RefreshTokenRequestDTO dto = RefreshTokenRequestDTO.builder()
                .refreshToken("valid_refresh_token")
                .build();

        Player suspendedPlayer = createPlayer(1L, "suspendido@utec.edu.pe", PlayerStatus.SUSPENDED);

        when(jwtService.extractEmail("valid_refresh_token")).thenReturn("suspendido@utec.edu.pe");
        when(playerRepository.findByEmail("suspendido@utec.edu.pe")).thenReturn(Optional.of(suspendedPlayer));

        // Act & Assert
        assertThatThrownBy(() -> authService.refreshToken(dto))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("suspendida");
    }
}

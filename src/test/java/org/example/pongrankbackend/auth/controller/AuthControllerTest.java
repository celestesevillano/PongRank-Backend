package org.example.pongrankbackend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.dto.PlayerResponseDTO;
import org.example.pongrankbackend.auth.dto.*;
import org.example.pongrankbackend.auth.service.AuthService;
import org.example.pongrankbackend.common.exception.EmailAlreadyExistsException;
import org.example.pongrankbackend.common.exception.GlobalExceptionHandler;
import org.example.pongrankbackend.common.exception.InvalidCredentialsException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.example.pongrankbackend.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, AuthControllerTest.SecurityTestConfig.class})
class AuthControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class SecurityTestConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    // ==========================================
    // Register Tests
    // ==========================================

    @Test
    @DisplayName("201 Created - Registro exitoso devuelve AuthResponseDTO")
    void register_Success_Returns201() throws Exception {
        PlayerRegisterRequestDTO request = PlayerRegisterRequestDTO.builder()
                .name("Carlos Gomez")
                .email("carlos@pongrank.com")
                .password("Password123")
                .whatsapp("+51987654321")
                .build();

        AuthResponseDTO response = AuthResponseDTO.builder()
                .token("access.jwt.token")
                .refreshToken("refresh.jwt.token")
                .expiresIn(86400000L)
                .tokenType("Bearer")
                .player(PlayerResponseDTO.builder()
                        .id(1L)
                        .name("Carlos Gomez")
                        .email("carlos@pongrank.com")
                        .role(Role.ROLE_USER)
                        .ratingGlicko(1200.0)
                        .build())
                .build();

        given(authService.register(any(PlayerRegisterRequestDTO.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token", is("access.jwt.token")))
                .andExpect(jsonPath("$.refreshToken", is("refresh.jwt.token")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.player.id", is(1)))
                .andExpect(jsonPath("$.player.email", is("carlos@pongrank.com")))
                .andExpect(jsonPath("$.player.role", is("ROLE_USER")));
    }

    @Test
    @DisplayName("400 Bad Request - Validación Jakarta falla por email inválido y campos en blanco")
    void register_ValidationError_Returns400() throws Exception {
        PlayerRegisterRequestDTO invalidRequest = PlayerRegisterRequestDTO.builder()
                .name("")
                .email("correo-invalido")
                .password("123") // Menos de 8 caracteres
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("email")))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/register")));
    }

    @Test
    @DisplayName("409 Conflict - Email ya registrado lanza EmailAlreadyExistsException")
    void register_EmailAlreadyExists_Returns409() throws Exception {
        PlayerRegisterRequestDTO request = PlayerRegisterRequestDTO.builder()
                .name("Carlos Gomez")
                .email("duplicado@pongrank.com")
                .password("Password123")
                .build();

        given(authService.register(any(PlayerRegisterRequestDTO.class)))
                .willThrow(new EmailAlreadyExistsException("El email duplicado@pongrank.com ya se encuentra registrado"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("ya se encuentra registrado")));
    }

    // ==========================================
    // Login Tests
    // ==========================================

    @Test
    @DisplayName("200 OK - Login exitoso devuelve token y datos del jugador")
    void login_Success_Returns200() throws Exception {
        AuthRequestDTO request = AuthRequestDTO.builder()
                .email("usuario@pongrank.com")
                .password("Password123")
                .build();

        AuthResponseDTO response = AuthResponseDTO.builder()
                .token("jwt.token.valido")
                .refreshToken("refresh.token.valido")
                .expiresIn(86400000L)
                .tokenType("Bearer")
                .player(PlayerResponseDTO.builder()
                        .id(2L)
                        .name("Ana Silva")
                        .email("usuario@pongrank.com")
                        .role(Role.ROLE_USER)
                        .build())
                .build();

        given(authService.login(any(AuthRequestDTO.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is("jwt.token.valido")))
                .andExpect(jsonPath("$.player.id", is(2)))
                .andExpect(jsonPath("$.player.email", is("usuario@pongrank.com")));
    }

    @Test
    @DisplayName("401 Unauthorized - Credenciales inválidas devuelven error uniforme")
    void login_InvalidCredentials_Returns401() throws Exception {
        AuthRequestDTO request = AuthRequestDTO.builder()
                .email("usuario@pongrank.com")
                .password("WrongPassword")
                .build();

        given(authService.login(any(AuthRequestDTO.class)))
                .willThrow(new InvalidCredentialsException("Credenciales inválidas"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Credenciales inválidas")));
    }

    @Test
    @DisplayName("400 Bad Request - Body JSON mal formado")
    void login_MalformedJson_Returns400() throws Exception {
        String malformedJson = "{\"email\": \"incompleto@pongrank.com\"";

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Cuerpo de la petición inválido o mal formado")));
    }

    // ==========================================
    // Refresh Tests
    // ==========================================

    @Test
    @DisplayName("200 OK - Refresco exitoso del token")
    void refresh_Success_Returns200() throws Exception {
        RefreshTokenRequestDTO request = RefreshTokenRequestDTO.builder()
                .refreshToken("valid.refresh.token")
                .build();

        AuthResponseDTO response = AuthResponseDTO.builder()
                .token("new.access.token")
                .refreshToken("valid.refresh.token")
                .expiresIn(86400000L)
                .tokenType("Bearer")
                .build();

        given(authService.refreshToken(any(RefreshTokenRequestDTO.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is("new.access.token")));
    }

    // ==========================================
    // Password Reset Tests
    // ==========================================

    @Test
    @DisplayName("200 OK - Solicitud de recuperación de contraseña")
    void forgotPassword_Success_Returns200() throws Exception {
        ForgotPasswordRequestDTO request = ForgotPasswordRequestDTO.builder()
                .email("usuario@pongrank.com")
                .build();

        willDoNothing().given(authService).forgotPassword(any(ForgotPasswordRequestDTO.class));

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("200 OK - Reseteo de contraseña con token válido")
    void resetPassword_Success_Returns200() throws Exception {
        ResetPasswordRequestDTO request = ResetPasswordRequestDTO.builder()
                .token("valid-reset-token")
                .newPassword("NewPassword123")
                .build();

        willDoNothing().given(authService).resetPassword(any(ResetPasswordRequestDTO.class));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("404 Not Found - Reseteo con token inexistente lanza ResourceNotFoundException")
    void resetPassword_TokenNotFound_Returns404() throws Exception {
        ResetPasswordRequestDTO request = ResetPasswordRequestDTO.builder()
                .token("token-inexistente")
                .newPassword("NewPassword123")
                .build();

        willThrow(new ResourceNotFoundException("Token no encontrado"))
                .given(authService).resetPassword(any(ResetPasswordRequestDTO.class));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("Token no encontrado")));
    }

    // ==========================================
    // Current User Tests
    // ==========================================

    @Test
    @DisplayName("200 OK - Obtiene el perfil del usuario autenticado con CustomUserDetails")
    void getCurrentUser_Authenticated_Returns200() throws Exception {
        Player mockPlayer = Player.builder()
                .id(1L)
                .email("carlos@pongrank.com")
                .password("pass")
                .role(Role.ROLE_USER)
                .status(PlayerStatus.ACTIVE)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(mockPlayer);

        PlayerResponseDTO profile = PlayerResponseDTO.builder()
                .id(1L)
                .name("Carlos Gomez")
                .email("carlos@pongrank.com")
                .role(Role.ROLE_USER)
                .ratingGlicko(1250.0)
                .build();

        given(authService.getCurrentUserProfile()).willReturn(profile);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.email", is("carlos@pongrank.com")))
                    .andExpect(jsonPath("$.name", is("Carlos Gomez")));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("401/403 - Petición sin autenticación es rechazada por seguridad")
    void getCurrentUser_Unauthenticated_ReturnsUnauthorized() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().is4xxClientError());
    }
}

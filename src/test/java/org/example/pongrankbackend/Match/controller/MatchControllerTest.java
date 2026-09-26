package org.example.pongrankbackend.Match.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.MatchType;
import org.example.pongrankbackend.Match.dto.*;
import org.example.pongrankbackend.Match.service.MatchService;
import org.example.pongrankbackend.MatchSet.dto.MatchSetRequestDTO;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.common.exception.GlobalExceptionHandler;
import org.example.pongrankbackend.common.exception.InvalidMatchStateException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.example.pongrankbackend.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MatchController.class)
@Import({GlobalExceptionHandler.class, MatchControllerTest.SecurityTestConfig.class})
class MatchControllerTest {

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
    private MatchService matchService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private CustomUserDetails authenticatedUser;

    @BeforeEach
    void setUp() {
        Player player = Player.builder()
                .id(1L)
                .email("jugador1@pongrank.com")
                .password("password")
                .role(Role.ROLE_USER)
                .status(PlayerStatus.ACTIVE)
                .build();
        authenticatedUser = new CustomUserDetails(player);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                authenticatedUser, null, authenticatedUser.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==========================================
    // 1. Crear Partido (POST /api/v1/matches)
    // ==========================================

    @Test
    @DisplayName("201 Created - Crear partido exitosamente devuelve MatchResponseDTO")
    void createMatch_Success_Returns201() throws Exception {
        MatchCreateRequestDTO request = MatchCreateRequestDTO.builder()
                .opponentId(2L)
                .format(MatchFormat.BO3)
                .matchType(MatchType.FRIEND)
                .build();

        MatchResponseDTO response = MatchResponseDTO.builder()
                .id(100L)
                .format(MatchFormat.BO3)
                .matchType(MatchType.FRIEND)
                .status(MatchStatus.CREATED)
                .player1(PlayerSummaryDTO.builder().id(1L).name("Jugador 1").build())
                .player2(PlayerSummaryDTO.builder().id(2L).name("Jugador 2").build())
                .build();

        given(matchService.createMatch(eq(1L), any(MatchCreateRequestDTO.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/matches")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(100)))
                .andExpect(jsonPath("$.matchType", is("FRIEND")))
                .andExpect(jsonPath("$.status", is("CREATED")))
                .andExpect(jsonPath("$.player1.id", is(1)))
                .andExpect(jsonPath("$.player2.id", is(2)));
    }

    @Test
    @DisplayName("400 Bad Request - Fallo de validación cuando falta matchType")
    void createMatch_MissingMatchType_Returns400() throws Exception {
        MatchCreateRequestDTO invalidRequest = MatchCreateRequestDTO.builder()
                .opponentId(2L)
                .matchType(null)
                .build();

        mockMvc.perform(post("/api/v1/matches")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("matchType")));
    }

    @Test
    @DisplayName("401/403 - Petición no autenticada es rechazada")
    void createMatch_Unauthenticated_ReturnsUnauthorized() throws Exception {
        SecurityContextHolder.clearContext();

        MatchCreateRequestDTO request = MatchCreateRequestDTO.builder()
                .opponentId(2L)
                .matchType(MatchType.FRIEND)
                .build();

        mockMvc.perform(post("/api/v1/matches")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    // ==========================================
    // 2. Obtener Partido por ID (GET /api/v1/matches/{id})
    // ==========================================

    @Test
    @DisplayName("200 OK - Obtener detalle del partido existente")
    void getMatchById_Success_Returns200() throws Exception {
        MatchDetailResponseDTO response = MatchDetailResponseDTO.builder()
                .id(100L)
                .format(MatchFormat.BO3)
                .matchType(MatchType.FRIEND)
                .status(MatchStatus.CONFIRMED)
                .scoreSummary("2 - 1")
                .player1(PlayerSummaryDTO.builder().id(1L).name("Jugador 1").build())
                .player2(PlayerSummaryDTO.builder().id(2L).name("Jugador 2").build())
                .sets(Collections.emptyList())
                .build();

        given(matchService.getMatchById(100L)).willReturn(response);

        mockMvc.perform(get("/api/v1/matches/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(100)))
                .andExpect(jsonPath("$.scoreSummary", is("2 - 1")))
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    @Test
    @DisplayName("404 Not Found - Partido inexistente lanza ResourceNotFoundException")
    void getMatchById_NotFound_Returns404() throws Exception {
        given(matchService.getMatchById(999L))
                .willThrow(new ResourceNotFoundException("Partido no encontrado con id: 999"));

        mockMvc.perform(get("/api/v1/matches/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("Partido no encontrado")));
    }

    // ==========================================
    // 3. Registrar Marcador (POST /api/v1/matches/{id}/submit)
    // ==========================================

    @Test
    @DisplayName("200 OK - Envío de marcador válido")
    void submitScore_Success_Returns200() throws Exception {
        MatchScoreSubmitDTO submitDTO = MatchScoreSubmitDTO.builder()
                .sets(List.of(
                        MatchSetRequestDTO.builder().setNumber(1).scorePlayer1(11).scorePlayer2(9).build(),
                        MatchSetRequestDTO.builder().setNumber(2).scorePlayer1(9).scorePlayer2(11).build(),
                        MatchSetRequestDTO.builder().setNumber(3).scorePlayer1(11).scorePlayer2(7).build()
                ))
                .build();

        MatchDetailResponseDTO response = MatchDetailResponseDTO.builder()
                .id(100L)
                .status(MatchStatus.PROPOSED_P1)
                .scoreSummary("2 - 1")
                .build();

        given(matchService.submitScore(eq(100L), eq(1L), any(MatchScoreSubmitDTO.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/matches/100/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(100)))
                .andExpect(jsonPath("$.scoreSummary", is("2 - 1")))
                .andExpect(jsonPath("$.status", is("PROPOSED_P1")));
    }

    @Test
    @DisplayName("400 Bad Request - Marcador con lista de sets vacía")
    void submitScore_EmptySets_Returns400() throws Exception {
        MatchScoreSubmitDTO invalidSubmit = MatchScoreSubmitDTO.builder()
                .sets(Collections.emptyList())
                .build();

        mockMvc.perform(post("/api/v1/matches/100/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidSubmit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("sets")));
    }

    @Test
    @DisplayName("400 Bad Request - Estado inválido de partido lanza InvalidMatchStateException")
    void submitScore_InvalidState_Returns400() throws Exception {
        MatchScoreSubmitDTO submitDTO = MatchScoreSubmitDTO.builder()
                .sets(List.of(MatchSetRequestDTO.builder().setNumber(1).scorePlayer1(11).scorePlayer2(5).build()))
                .build();

        given(matchService.submitScore(eq(100L), eq(1L), any(MatchScoreSubmitDTO.class)))
                .willThrow(new InvalidMatchStateException("El partido ya fue confirmado previamente"));

        mockMvc.perform(post("/api/v1/matches/100/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("ya fue confirmado")));
    }

    // ==========================================
    // 4. Confirmar Partido (PUT /api/v1/matches/{id}/confirm)
    // ==========================================

    @Test
    @DisplayName("200 OK - Confirmación exitosa del resultado")
    void confirmMatch_Success_Returns200() throws Exception {
        MatchDetailResponseDTO response = MatchDetailResponseDTO.builder()
                .id(100L)
                .status(MatchStatus.CONFIRMED)
                .winnerId(1L)
                .build();

        given(matchService.confirmMatch(100L, 1L)).willReturn(response);

        mockMvc.perform(put("/api/v1/matches/100/confirm").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(100)))
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.winnerId", is(1)));
    }

    @Test
    @DisplayName("403 Forbidden - Jugador no autorizado lanza UnauthorizedActionException")
    void confirmMatch_UnauthorizedPlayer_Returns403() throws Exception {
        given(matchService.confirmMatch(100L, 1L))
                .willThrow(new UnauthorizedActionException("No puedes confirmar tu propio marcador enviado"));

        mockMvc.perform(put("/api/v1/matches/100/confirm").with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", containsString("No puedes confirmar")));
    }

    // ==========================================
    // 5. Disputar Partido (PUT /api/v1/matches/{id}/dispute)
    // ==========================================

    @Test
    @DisplayName("200 OK - Disputa de resultado exitosa")
    void disputeMatch_Success_Returns200() throws Exception {
        MatchDisputeRequestDTO request = MatchDisputeRequestDTO.builder()
                .reason("Marcador incorrecto en el segundo set")
                .build();

        MatchDetailResponseDTO response = MatchDetailResponseDTO.builder()
                .id(100L)
                .status(MatchStatus.DISPUTED)
                .disputeReason("Marcador incorrecto en el segundo set")
                .build();

        given(matchService.disputeMatch(eq(100L), eq(1L), any(MatchDisputeRequestDTO.class))).willReturn(response);

        mockMvc.perform(put("/api/v1/matches/100/dispute")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(100)))
                .andExpect(jsonPath("$.status", is("DISPUTED")))
                .andExpect(jsonPath("$.disputeReason", is("Marcador incorrecto en el segundo set")));
    }

    @Test
    @DisplayName("400 Bad Request - Motivo de disputa en blanco")
    void disputeMatch_BlankReason_Returns400() throws Exception {
        MatchDisputeRequestDTO invalidRequest = MatchDisputeRequestDTO.builder()
                .reason("")
                .build();

        mockMvc.perform(put("/api/v1/matches/100/dispute")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("motivo de la disputa")));
    }

    // ==========================================
    // 6. Cancelar Partido (PUT /api/v1/matches/{id}/cancel)
    // ==========================================

    @Test
    @DisplayName("200 OK - Cancelar partido exitosamente")
    void cancelMatch_Success_Returns200() throws Exception {
        MatchResponseDTO response = MatchResponseDTO.builder()
                .id(100L)
                .status(MatchStatus.CANCELLED)
                .build();

        given(matchService.cancelMatch(100L, 1L)).willReturn(response);

        mockMvc.perform(put("/api/v1/matches/100/cancel").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(100)))
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    // ==========================================
    // 7. Listados con Paginación
    // ==========================================

    @Test
    @DisplayName("200 OK - Listado general de partidos paginado")
    void getAllMatches_Success_ReturnsPage() throws Exception {
        MatchResponseDTO match = MatchResponseDTO.builder()
                .id(100L)
                .matchType(MatchType.FRIEND)
                .status(MatchStatus.CONFIRMED)
                .build();

        given(matchService.getAllMatches(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(match)));

        mockMvc.perform(get("/api/v1/matches?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(100)));
    }

    @Test
    @DisplayName("200 OK - Historial de partidos por jugador")
    void getMatchesByPlayer_Success_ReturnsPage() throws Exception {
        MatchResponseDTO match = MatchResponseDTO.builder()
                .id(100L)
                .matchType(MatchType.FRIEND)
                .status(MatchStatus.CONFIRMED)
                .build();

        given(matchService.getMatchesByPlayer(eq(1L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(match)));

        mockMvc.perform(get("/api/v1/matches/player/1?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(100)));
    }
}

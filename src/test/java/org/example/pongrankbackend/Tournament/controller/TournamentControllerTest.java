package org.example.pongrankbackend.Tournament.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Tournament.TournamentMatchStatus;
import org.example.pongrankbackend.Tournament.TournamentStage;
import org.example.pongrankbackend.Tournament.TournamentStatus;
import org.example.pongrankbackend.Tournament.TournamentType;
import org.example.pongrankbackend.Tournament.dto.*;
import org.example.pongrankbackend.Tournament.service.TournamentService;
import org.example.pongrankbackend.common.exception.GlobalExceptionHandler;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TournamentController.class)
@Import({GlobalExceptionHandler.class, TournamentControllerTest.SecurityTestConfig.class})
class TournamentControllerTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TournamentService tournamentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private CustomUserDetails authenticatedUser;

    @BeforeEach
    void setUp() {
        Player player = Player.builder()
                .id(1L)
                .email("admin@pongrank.com")
                .password("password")
                .role(Role.ROLE_CLUB_ADMIN)
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

    @Test
    @DisplayName("201 Created - Creación de torneo exitosa")
    void createTournament_Success_Returns201() throws Exception {
        TournamentCreateRequestDTO request = TournamentCreateRequestDTO.builder()
                .clubId(10L)
                .name("Torneo Primavera 2026")
                .type(TournamentType.OPEN)
                .matchFormat(MatchFormat.BO5)
                .build();

        TournamentResponseDTO response = TournamentResponseDTO.builder()
                .id(50L)
                .name("Torneo Primavera 2026")
                .clubId(10L)
                .clubName("Club Lima")
                .type(TournamentType.OPEN)
                .matchFormat(MatchFormat.BO5)
                .status(TournamentStatus.OPEN)
                .build();

        given(tournamentService.createTournament(any(TournamentCreateRequestDTO.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/tournaments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(50)))
                .andExpect(jsonPath("$.name", is("Torneo Primavera 2026")))
                .andExpect(jsonPath("$.status", is("OPEN")));
    }

    @Test
    @DisplayName("400 Bad Request - Creación de torneo falla si faltan campos obligatorios")
    void createTournament_ValidationError_Returns400() throws Exception {
        TournamentCreateRequestDTO invalidRequest = TournamentCreateRequestDTO.builder()
                .clubId(null)
                .name("")
                .build();

        mockMvc.perform(post("/api/v1/tournaments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    @Test
    @DisplayName("200 OK - Obtener torneo existente por id")
    void getTournamentById_Success_Returns200() throws Exception {
        TournamentResponseDTO response = TournamentResponseDTO.builder()
                .id(50L)
                .name("Torneo Nacional")
                .type(TournamentType.OPEN)
                .matchFormat(MatchFormat.BO5)
                .status(TournamentStatus.OPEN)
                .build();

        given(tournamentService.getTournamentById(50L)).willReturn(response);

        mockMvc.perform(get("/api/v1/tournaments/50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(50)))
                .andExpect(jsonPath("$.name", is("Torneo Nacional")));
    }

    @Test
    @DisplayName("404 Not Found - Torneo inexistente lanza ResourceNotFoundException")
    void getTournamentById_NotFound_Returns404() throws Exception {
        given(tournamentService.getTournamentById(999L))
                .willThrow(new ResourceNotFoundException("Torneo no encontrado con id: 999"));

        mockMvc.perform(get("/api/v1/tournaments/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("Torneo no encontrado")));
    }

    @Test
    @DisplayName("201 Created - Inscripción de participante en torneo")
    void addParticipant_Success_Returns201() throws Exception {
        TournamentParticipantRequestDTO request = TournamentParticipantRequestDTO.builder()
                .playerId(100L)
                .build();

        TournamentParticipantResponseDTO response = TournamentParticipantResponseDTO.builder()
                .id(1L)
                .player(PlayerSummaryDTO.builder().id(100L).name("Jugador Uno").build())
                .seed(1)
                .build();

        given(tournamentService.addParticipant(eq(50L), any(TournamentParticipantRequestDTO.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/tournaments/50/participants")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.seed", is(1)))
                .andExpect(jsonPath("$.player.id", is(100)));
    }

    @Test
    @DisplayName("204 No Content - Eliminación de participante de torneo")
    void removeParticipant_Success_Returns204() throws Exception {
        doNothing().when(tournamentService).removeParticipant(50L, 100L);

        mockMvc.perform(delete("/api/v1/tournaments/50/participants/100")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("200 OK - Iniciar torneo genera grupos o llaves")
    void startTournament_Success_Returns200() throws Exception {
        TournamentResponseDTO response = TournamentResponseDTO.builder()
                .id(50L)
                .name("Torneo Activo")
                .status(TournamentStatus.GROUP_STAGE)
                .build();

        given(tournamentService.startTournament(50L)).willReturn(response);

        mockMvc.perform(post("/api/v1/tournaments/50/start")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(50)))
                .andExpect(jsonPath("$.status", is("GROUP_STAGE")));
    }

    @Test
    @DisplayName("200 OK - Obtener lista de partidos del torneo")
    void getMatches_Success_Returns200() throws Exception {
        TournamentMatchResponseDTO match1 = TournamentMatchResponseDTO.builder()
                .id(200L)
                .stage(TournamentStage.GROUP)
                .groupNumber(1)
                .status(TournamentMatchStatus.SCHEDULED)
                .build();

        given(tournamentService.getMatches(50L)).willReturn(List.of(match1));

        mockMvc.perform(get("/api/v1/tournaments/50/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(200)))
                .andExpect(jsonPath("$[0].stage", is("GROUP")));
    }

    @Test
    @DisplayName("200 OK - Declarar W.O. en partido de torneo")
    void declareWalkover_Success_Returns200() throws Exception {
        TournamentWalkoverRequestDTO request = TournamentWalkoverRequestDTO.builder()
                .absentPlayerId(101L)
                .build();

        TournamentMatchResponseDTO response = TournamentMatchResponseDTO.builder()
                .id(200L)
                .status(TournamentMatchStatus.WALKOVER)
                .build();

        given(tournamentService.declareWalkover(eq(50L), eq(200L), any(TournamentWalkoverRequestDTO.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/tournaments/50/matches/200/walkover")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(200)))
                .andExpect(jsonPath("$.status", is("WALKOVER")));
    }

    @Test
    @DisplayName("200 OK - Sincronizar resultados de partidos en torneo")
    void syncMatchResults_Success_Returns200() throws Exception {
        TournamentResponseDTO response = TournamentResponseDTO.builder()
                .id(50L)
                .name("Torneo Sincronizado")
                .status(TournamentStatus.KNOCKOUT_STAGE)
                .build();

        given(tournamentService.syncMatchResults(50L)).willReturn(response);

        mockMvc.perform(post("/api/v1/tournaments/50/sync-results")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(50)))
                .andExpect(jsonPath("$.status", is("KNOCKOUT_STAGE")));
    }

    @Test
    @DisplayName("401/403 - Petición no autenticada en endpoint protegido es rechazada")
    void startTournament_Unauthenticated_ReturnsUnauthorized() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/api/v1/tournaments/50/start")
                        .with(csrf()))
                .andExpect(status().is4xxClientError());
    }
}

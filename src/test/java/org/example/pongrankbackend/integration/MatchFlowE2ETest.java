package org.example.pongrankbackend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.MatchType;
import org.example.pongrankbackend.Match.dto.MatchCreateRequestDTO;
import org.example.pongrankbackend.Match.dto.MatchScoreSubmitDTO;
import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.MatchSet.dto.MatchSetRequestDTO;
import org.example.pongrankbackend.MatchSet.repository.MatchSetRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.auth.dto.AuthRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MatchFlowE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchSetRepository matchSetRepository;

    @Autowired
    private PlayerRepository playerRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("E2E User Journey Completo: Registro Juan -> Registro Pedro -> Login Juan -> Crear Partido -> Submit Marcador -> Confirmar Partido -> Verificación BD")
    void completeMatchUserJourneyFlow() throws Exception {
        // =========================================================================
        // PASO 1: Registro de Juan (Jugador 1)
        // =========================================================================
        PlayerRegisterRequestDTO juanRegister = PlayerRegisterRequestDTO.builder()
                .name("Juan Perez")
                .email("juan.journey@pongrank.com")
                .password("Password123")
                .whatsapp("+51999111222")
                .shareContact(true)
                .categoryFdptm("Segunda")
                .federatedDeclared(true)
                .build();

        MvcResult juanRegisterResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(juanRegister)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.player.id").isNumber())
                .andExpect(jsonPath("$.player.email", is("juan.journey@pongrank.com")))
                .andReturn();

        JsonNode juanRegisterJson = objectMapper.readTree(juanRegisterResult.getResponse().getContentAsString());
        Long juanId = juanRegisterJson.get("player").get("id").asLong();

        // =========================================================================
        // PASO 2: Registro de Pedro (Jugador 2)
        // =========================================================================
        PlayerRegisterRequestDTO pedroRegister = PlayerRegisterRequestDTO.builder()
                .name("Pedro Gomez")
                .email("pedro.journey@pongrank.com")
                .password("Password123")
                .whatsapp("+51999333444")
                .shareContact(true)
                .categoryFdptm("Segunda")
                .federatedDeclared(false)
                .build();

        MvcResult pedroRegisterResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedroRegister)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.player.id").isNumber())
                .andExpect(jsonPath("$.player.email", is("pedro.journey@pongrank.com")))
                .andReturn();

        JsonNode pedroRegisterJson = objectMapper.readTree(pedroRegisterResult.getResponse().getContentAsString());
        Long pedroId = pedroRegisterJson.get("player").get("id").asLong();
        String pedroToken = pedroRegisterJson.get("token").asText();

        // =========================================================================
        // PASO 3: Login de Juan para validar autenticación y obtención de JWT
        // =========================================================================
        AuthRequestDTO juanLogin = AuthRequestDTO.builder()
                .email("juan.journey@pongrank.com")
                .password("Password123")
                .build();

        MvcResult juanLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(juanLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode juanLoginJson = objectMapper.readTree(juanLoginResult.getResponse().getContentAsString());
        String juanToken = juanLoginJson.get("token").asText();

        // =========================================================================
        // PASO 4: Juan crea el partido retando a Pedro con cabecera Authorization: Bearer <token>
        // =========================================================================
        MatchCreateRequestDTO createMatchRequest = MatchCreateRequestDTO.builder()
                .opponentId(pedroId)
                .format(MatchFormat.BO3)
                .matchType(MatchType.LOCATION)
                .latitude(BigDecimal.valueOf(-12.0850))
                .longitude(BigDecimal.valueOf(-77.0500))
                .opponentLatitude(BigDecimal.valueOf(-12.0850))
                .opponentLongitude(BigDecimal.valueOf(-77.0500))
                .build();

        MvcResult matchCreateResult = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", "Bearer " + juanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createMatchRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status", is("CREATED")))
                .andExpect(jsonPath("$.player1.id", is(juanId.intValue())))
                .andExpect(jsonPath("$.player2.id", is(pedroId.intValue())))
                .andReturn();

        JsonNode matchCreateJson = objectMapper.readTree(matchCreateResult.getResponse().getContentAsString());
        Long matchId = matchCreateJson.get("id").asLong();

        // =========================================================================
        // PASO 5: Juan reporta el marcador (Submit Score: 2 sets a 0)
        // =========================================================================
        MatchScoreSubmitDTO submitScore = MatchScoreSubmitDTO.builder()
                .sets(List.of(
                        MatchSetRequestDTO.builder().setNumber(1).scorePlayer1(11).scorePlayer2(8).build(),
                        MatchSetRequestDTO.builder().setNumber(2).scorePlayer1(11).scorePlayer2(9).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/matches/" + matchId + "/submit")
                        .header("Authorization", "Bearer " + juanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitScore)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PROPOSED_P1")))
                .andExpect(jsonPath("$.sets").isArray());

        // =========================================================================
        // PASO 6: Pedro confirma el resultado con su propio token JWT
        // =========================================================================
        mockMvc.perform(put("/api/v1/matches/" + matchId + "/confirm")
                        .header("Authorization", "Bearer " + pedroToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.winnerId", is(juanId.intValue())));

        // =========================================================================
        // PASO 7: Verificación en Base de Datos Real (H2)
        // =========================================================================
        Optional<Match> confirmedMatchOpt = matchRepository.findById(matchId);
        assertThat(confirmedMatchOpt).isPresent();

        Match confirmedMatch = confirmedMatchOpt.get();
        assertThat(confirmedMatch.getStatus()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(confirmedMatch.getWinner()).isNotNull();
        assertThat(confirmedMatch.getWinner().getId()).isEqualTo(juanId);
        assertThat(confirmedMatch.getConfirmedAt()).isNotNull();
        assertThat(matchSetRepository.findByMatchIdOrderBySetNumberAsc(matchId)).hasSize(2);

        Optional<Player> juanDbOpt = playerRepository.findById(juanId);
        assertThat(juanDbOpt).isPresent();
        assertThat(juanDbOpt.get().getName()).isEqualTo("Juan Perez");

        Optional<Player> pedroDbOpt = playerRepository.findById(pedroId);
        assertThat(pedroDbOpt).isPresent();
        assertThat(pedroDbOpt.get().getName()).isEqualTo("Pedro Gomez");
    }
}

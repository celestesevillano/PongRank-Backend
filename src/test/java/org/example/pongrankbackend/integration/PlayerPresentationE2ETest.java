package org.example.pongrankbackend.integration;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.pongrankbackend.Match.Match;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.MatchType;
import org.example.pongrankbackend.Match.repository.MatchRepository;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PlayerPresentationE2ETest {

    private static final String PRESENTATION = "/api/v1/players/me/profile";
    @Autowired private MockMvc mvc;
    @Autowired private PlayerRepository players;
    @Autowired private MatchRepository matches;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void profilePersistsAtomicallyAndCanBeViewedWithoutPrivateFields() throws Exception {
        var owner = register("Titular");
        var visitor = register("Visitante");
        String path = "/api/v1/players/" + owner.id() + "/profile";
        var image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "JPEG", image);
        String photo = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image.toByteArray());

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(put(PRESENTATION).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sin sesión\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(PRESENTATION).header("Authorization", owner.auth()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Nombre actualizado", "city", "Lima",
                                "description", "Entreno los fines de semana", "photo", photo,
                                "instagram", "https://www.instagram.com/jugador/"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nombre actualizado"))
                .andExpect(jsonPath("$.photo").value(photo));

        mvc.perform(put(PRESENTATION).header("Authorization", owner.auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No guardar\",\"instagram\":\"https://instagram.com.ejemplo.com/jugador\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path).header("Authorization", visitor.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nombre actualizado"))
                .andExpect(jsonPath("$.city").value("Lima"))
                .andExpect(jsonPath("$.photo").value(photo))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.whatsapp").doesNotExist());
        mvc.perform(get("/api/v1/players/" + owner.id() + "/summary").header("Authorization", visitor.auth()))
                .andExpect(jsonPath("$.name").value("Nombre actualizado"))
                .andExpect(jsonPath("$.photo").doesNotExist());

        mvc.perform(put(PRESENTATION).header("Authorization", owner.auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nombre actualizado\",\"photo\":\"data:image/jpeg;base64,AAAA\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(PRESENTATION).header("Authorization", owner.auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nombre actualizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photo").isEmpty())
                .andExpect(jsonPath("$.instagram").isEmpty());
    }

    @Test
    void onlyTheOpponentCanReadOptedInWhatsappAndOnlyForAnActiveMatch() throws Exception {
        var owner = register("Titular");
        var opponent = register("Rival");
        var visitor = register("Ajeno");
        var ownerPlayer = players.findById(owner.id()).orElseThrow();
        var rival = players.findById(opponent.id()).orElseThrow();
        ownerPlayer.setWhatsapp("+51999888777");
        ownerPlayer.setShareContact(true);
        players.save(ownerPlayer);
        var match = matches.save(Match.builder().player1(ownerPlayer).player2(rival)
                .status(MatchStatus.CREATED).format(MatchFormat.BO3).matchType(MatchType.FRIEND).build());

        mvc.perform(get("/api/v1/players/" + owner.id() + "/summary").header("Authorization", visitor.auth()))
                .andExpect(jsonPath("$.whatsapp").isEmpty());
        mvc.perform(get("/api/v1/players/" + owner.id()).header("Authorization", visitor.auth()))
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.whatsapp").isEmpty());
        String path = "/api/v1/matches/" + match.getId();
        mvc.perform(get(path).header("Authorization", visitor.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.player1.whatsapp").isEmpty());
        mvc.perform(get(path).header("Authorization", opponent.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.player1.whatsapp").value("+51999888777"));
        match.setStatus(MatchStatus.CANCELLED);
        matches.save(match);
        mvc.perform(get(path).header("Authorization", opponent.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.player1.whatsapp").isEmpty());
    }

    private Account register(String name) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name, "email", UUID.randomUUID() + "@example.com",
                                "gender", "PREFER_NOT_TO_SAY", "password", "TestPassword123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var body = json.readTree(response);
        return new Account(body.path("player").path("id").asLong(), "Bearer " + body.path("token").asText());
    }

    private record Account(long id, String auth) {
    }
}

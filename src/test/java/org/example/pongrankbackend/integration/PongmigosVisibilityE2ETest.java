package org.example.pongrankbackend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.pongrankbackend.Friendship.Friendship;
import org.example.pongrankbackend.Friendship.FriendshipStatus;
import org.example.pongrankbackend.Friendship.repository.FriendshipRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PongmigosVisibilityE2ETest {
    private static final String SETTINGS = "/api/v1/friendships/me/visibility";

    @Autowired private MockMvc mvc;
    @Autowired private PlayerRepository players;
    @Autowired private FriendshipRepository friendships;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void revealsOnlyCountUntilTheOwnerSharesAndHidesTheListAgain() throws Exception {
        Account owner = register("Titular");
        Account viewer = register("Visitante");
        Player shared = createPlayer("Ada", PlayerStatus.ACTIVE);
        Player other = createPlayer("Bea", PlayerStatus.ACTIVE);
        Player deleted = createPlayer("Cuenta anterior", PlayerStatus.DELETED);
        Player pending = createPlayer("Pendiente", PlayerStatus.ACTIVE);

        Player ownerPlayer = players.findById(owner.id()).orElseThrow();
        // Simula una cuenta que existía antes de agregar la columna.
        ownerPlayer.setPongmigosVisibility(null);
        players.save(ownerPlayer);

        link(ownerPlayer, shared, FriendshipStatus.ACCEPTED);
        link(other, ownerPlayer, FriendshipStatus.ACCEPTED);
        link(ownerPlayer, deleted, FriendshipStatus.ACCEPTED);
        link(pending, ownerPlayer, FriendshipStatus.PENDING);
        link(players.findById(viewer.id()).orElseThrow(), shared, FriendshipStatus.ACCEPTED);

        String path = "/api/v1/friendships/players/" + owner.id() + "/public-list";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(SETTINGS).header("Authorization", owner.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("ONLY_ME"));
        mvc.perform(get(path).header("Authorization", viewer.auth()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.canViewList").value(false))
                .andExpect(jsonPath("$.pongmigos").doesNotExist());

        mvc.perform(get(path).header("Authorization", owner.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pongmigos.content.length()").value(2));

        mvc.perform(patch(SETTINGS).header("Authorization", viewer.auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"EVERYONE\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", viewer.auth()))
                .andExpect(jsonPath("$.canViewList").value(false));

        mvc.perform(patch(SETTINGS).header("Authorization", owner.auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(SETTINGS).header("Authorization", owner.auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":0}"))
                .andExpect(status().isBadRequest());
        setVisibility(owner, "EVERYONE");

        mvc.perform(get(path).param("size", "1").header("Authorization", viewer.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canViewList").value(true))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pongmigos.totalPages").value(2))
                .andExpect(jsonPath("$.pongmigos.content[0].name").value("Ada"))
                .andExpect(jsonPath("$.pongmigos.content[0].mutual").value(true))
                .andExpect(jsonPath("$.pongmigos.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.pongmigos.content[0].whatsapp").doesNotExist());
        mvc.perform(get(path).param("page", "1").param("size", "1").header("Authorization", viewer.auth()))
                .andExpect(jsonPath("$.pongmigos.content[0].name").value("Bea"))
                .andExpect(jsonPath("$.pongmigos.content[0].mutual").value(false));

        setVisibility(owner, "ONLY_ME");
        mvc.perform(get(path).header("Authorization", viewer.auth()))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.canViewList").value(false))
                .andExpect(jsonPath("$.pongmigos").doesNotExist());
    }

    private void setVisibility(Account account, String visibility) throws Exception {
        mvc.perform(patch(SETTINGS).header("Authorization", account.auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("visibility", visibility))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value(visibility));
    }

    private Account register(String name) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", name,
                                "email", UUID.randomUUID() + "@example.com",
                                "gender", "PREFER_NOT_TO_SAY",
                                "password", "TestPassword123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode body = json.readTree(response);
        return new Account(body.path("player").path("id").asLong(), "Bearer " + body.path("token").asText());
    }

    private Player createPlayer(String name, PlayerStatus status) {
        return players.save(Player.builder().name(name).email(UUID.randomUUID() + "@example.com")
                .password("test-fixture").status(status).build());
    }

    private void link(Player sender, Player recipient, FriendshipStatus status) {
        friendships.save(Friendship.builder().playerA(sender).playerB(recipient).status(status).build());
    }

    private record Account(Long id, String auth) {}
}

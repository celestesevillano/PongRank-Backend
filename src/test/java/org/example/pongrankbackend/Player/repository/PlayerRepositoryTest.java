package org.example.pongrankbackend.Player.repository;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class PlayerRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlayerRepository playerRepository;

    private Player savedPlayer;

    @BeforeEach
    void setUp() {
        Player player = Player.builder()
                .name("Mateo Torres")
                .email("mateo@pongrank.com")
                .password("$2a$10$encodedPasswordHashHere12345")
                .whatsapp("+51999888777")
                .shareContact(true)
                .categoryFdptm("Segunda")
                .federatedDeclared(true)
                .ratingGlicko(1650.0)
                .ratingDeviation(200.0)
                .volatility(0.059)
                .role(Role.ROLE_USER)
                .status(PlayerStatus.ACTIVE)
                .build();
        savedPlayer = entityManager.persistAndFlush(player);
    }

    @Test
    @DisplayName("findByEmail - Retorna el jugador existente correctamente")
    void findByEmail_ExistingEmail_ReturnsPlayer() {
        Optional<Player> found = playerRepository.findByEmail("mateo@pongrank.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedPlayer.getId());
        assertThat(found.get().getName()).isEqualTo("Mateo Torres");
        assertThat(found.get().getRatingGlicko()).isEqualTo(1650.0);
        assertThat(found.get().getStatus()).isEqualTo(PlayerStatus.ACTIVE);
    }

    @Test
    @DisplayName("findByEmail - Retorna vacío cuando el email no existe")
    void findByEmail_NonExistingEmail_ReturnsEmpty() {
        Optional<Player> found = playerRepository.findByEmail("noexiste@pongrank.com");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByEmail - Retorna true si el email ya está registrado")
    void existsByEmail_ExistingEmail_ReturnsTrue() {
        boolean exists = playerRepository.existsByEmail("mateo@pongrank.com");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByEmail - Retorna false si el email no está registrado")
    void existsByEmail_NonExistingEmail_ReturnsFalse() {
        boolean exists = playerRepository.existsByEmail("libre@pongrank.com");

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Constraint unique email - Persistir email duplicado lanza DataIntegrityViolationException")
    void save_DuplicateEmail_ThrowsDataIntegrityViolationException() {
        Player duplicate = Player.builder()
                .name("Mateo Clon")
                .email("mateo@pongrank.com")
                .password("Password999")
                .role(Role.ROLE_USER)
                .status(PlayerStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> {
            playerRepository.saveAndFlush(duplicate);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}

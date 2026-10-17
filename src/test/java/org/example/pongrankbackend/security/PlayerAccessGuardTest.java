package org.example.pongrankbackend.security;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerAccessGuardTest {

    private final PlayerAccessGuard guard = new PlayerAccessGuard();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long id, Role role) {
        Player player = Player.builder()
                .id(id)
                .email("player" + id + "@utec.edu.pe")
                .password("encoded")
                .role(role)
                .status(PlayerStatus.ACTIVE)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(player);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @Test
    @DisplayName("requireSelfOrAdmin: permite al propio jugador")
    void requireSelfOrAdmin_SamePlayer_Allows() {
        authenticateAs(1L, Role.ROLE_USER);

        assertThatCode(() -> guard.requireSelfOrAdmin(1L, "denegado")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireSelfOrAdmin: permite a un SYSTEM_ADMIN sobre otro jugador")
    void requireSelfOrAdmin_SystemAdmin_Allows() {
        authenticateAs(99L, Role.ROLE_SYSTEM_ADMIN);

        assertThatCode(() -> guard.requireSelfOrAdmin(1L, "denegado")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireSelfOrAdmin: deniega a otro jugador sin rol de admin con el mensaje indicado")
    void requireSelfOrAdmin_OtherPlayer_ThrowsWithMessage() {
        authenticateAs(2L, Role.ROLE_USER);

        assertThatThrownBy(() -> guard.requireSelfOrAdmin(1L, "denegado"))
                .isInstanceOf(UnauthorizedActionException.class)
                .hasMessage("denegado");
    }

    @Test
    @DisplayName("requireSelfOrAdmin: un CLUB_ADMIN no tiene acceso a datos de otro jugador")
    void requireSelfOrAdmin_ClubAdmin_IsNotSystemAdmin() {
        authenticateAs(2L, Role.ROLE_CLUB_ADMIN);

        assertThatThrownBy(() -> guard.requireSelfOrAdmin(1L, "denegado"))
                .isInstanceOf(UnauthorizedActionException.class);
    }

    @Test
    @DisplayName("requireSelfOrAdmin: sin sesión lanza UnauthorizedActionException")
    void requireSelfOrAdmin_NoSession_Throws() {
        assertThatThrownBy(() -> guard.requireSelfOrAdmin(1L, "denegado"))
                .isInstanceOf(UnauthorizedActionException.class);
    }
}

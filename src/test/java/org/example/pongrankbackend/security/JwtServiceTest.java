package org.example.pongrankbackend.security;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.PlayerStatus;
import org.example.pongrankbackend.Player.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L);
        ReflectionTestUtils.setField(jwtService, "jwtRefreshExpiration", 604800000L);
    }

    private CustomUserDetails createUserDetails(Long id, String email, Role role) {
        Player player = Player.builder()
                .id(id)
                .name("Test User")
                .email(email)
                .password("encoded_pass")
                .role(role)
                .status(PlayerStatus.ACTIVE)
                .build();
        return new CustomUserDetails(player);
    }

    @Test
    @DisplayName("shouldGenerateValidAccessToken")
    void shouldGenerateValidAccessToken() {
        CustomUserDetails userDetails = createUserDetails(1L, "user@pongrank.com", Role.ROLE_USER);

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("user@pongrank.com");
        assertThat(jwtService.extractUserId(token)).isEqualTo(1L);
        assertThat(jwtService.extractRole(token)).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("shouldGenerateValidRefreshToken")
    void shouldGenerateValidRefreshToken() {
        CustomUserDetails userDetails = createUserDetails(2L, "admin@pongrank.com", Role.ROLE_CLUB_ADMIN);

        String refreshToken = jwtService.generateRefreshToken(userDetails);

        assertThat(refreshToken).isNotBlank();
        assertThat(jwtService.isRefreshTokenValid(refreshToken, userDetails)).isTrue();
        assertThat(jwtService.isTokenValid(refreshToken, userDetails)).isFalse();
        assertThat(jwtService.extractEmail(refreshToken)).isEqualTo("admin@pongrank.com");
        assertThat(jwtService.extractUserId(refreshToken)).isEqualTo(2L);
    }

    @Test
    @DisplayName("shouldReturnFalseWhenTokenBelongsToDifferentUser")
    void shouldReturnFalseWhenTokenBelongsToDifferentUser() {
        CustomUserDetails userA = createUserDetails(1L, "userA@pongrank.com", Role.ROLE_USER);
        CustomUserDetails userB = createUserDetails(2L, "userB@pongrank.com", Role.ROLE_USER);

        String tokenA = jwtService.generateAccessToken(userA);

        assertThat(jwtService.isTokenValid(tokenA, userB)).isFalse();
    }

    @Test
    @DisplayName("shouldReturnFalseWhenTokenIsMalformed")
    void shouldReturnFalseWhenTokenIsMalformed() {
        CustomUserDetails userDetails = createUserDetails(1L, "user@pongrank.com", Role.ROLE_USER);

        assertThat(jwtService.isTokenValid("malformed.token.string", userDetails)).isFalse();
    }

    @Test
    @DisplayName("shouldDetectExpiredTokenWhenExpirationIsNegative")
    void shouldDetectExpiredTokenWhenExpirationIsNegative() {
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L); // Ya expirado al crearse
        CustomUserDetails userDetails = createUserDetails(1L, "user@pongrank.com", Role.ROLE_USER);

        String token = jwtService.generateAccessToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
    }
}

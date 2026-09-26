package org.example.pongrankbackend.Match.service;

import org.example.pongrankbackend.Match.MatchType;
import org.example.pongrankbackend.Match.dto.MatchCreateRequestDTO;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.common.exception.InvalidMatchStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchRuleValidatorTest {

    private final MatchRuleValidator validator = new MatchRuleValidator();

    private Player playerWithRating(Long id, double rating) {
        return Player.builder().id(id).ratingGlicko(rating).build();
    }

    private MatchCreateRequestDTO locationDto(BigDecimal lat, BigDecimal lng, BigDecimal opponentLat, BigDecimal opponentLng) {
        return MatchCreateRequestDTO.builder()
                .matchType(MatchType.LOCATION)
                .latitude(lat)
                .longitude(lng)
                .opponentLatitude(opponentLat)
                .opponentLongitude(opponentLng)
                .build();
    }

    @Test
    @DisplayName("validateMatchCreation: LOCATION con oponente de nivel similar no lanza excepción")
    void validateMatchCreation_LocationSimilarRating_DoesNotThrow() {
        Player p1 = playerWithRating(1L, 1500.0);
        Player p2 = playerWithRating(2L, 1600.0);
        MatchCreateRequestDTO dto = locationDto(
                new BigDecimal("-12.0"), new BigDecimal("-77.0"),
                new BigDecimal("-12.001"), new BigDecimal("-77.001"));

        assertThatCode(() -> validator.validateMatchCreation(p1, p2, dto, Optional.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateMatchCreation: LOCATION rechaza oponente cuya diferencia de rating excede el máximo")
    void validateMatchCreation_LocationRatingGapTooHigh_Throws() {
        Player p1 = playerWithRating(1L, 1500.0);
        Player p2 = playerWithRating(2L, 1500.0 + MatchRuleValidator.MAX_LOCATION_RATING_GAP + 1);
        MatchCreateRequestDTO dto = locationDto(
                new BigDecimal("-12.0"), new BigDecimal("-77.0"),
                new BigDecimal("-12.001"), new BigDecimal("-77.001"));

        assertThatThrownBy(() -> validator.validateMatchCreation(p1, p2, dto, Optional.empty()))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessageContaining("diferencia de nivel");
    }

    @Test
    @DisplayName("validateMatchCreation: LOCATION sin oponente todavía (partido abierto) no valida rating")
    void validateMatchCreation_LocationNoOpponentYet_DoesNotThrow() {
        Player p1 = playerWithRating(1L, 1500.0);
        MatchCreateRequestDTO dto = MatchCreateRequestDTO.builder()
                .matchType(MatchType.LOCATION)
                .latitude(new BigDecimal("-12.0"))
                .longitude(new BigDecimal("-77.0"))
                .build();

        assertThatCode(() -> validator.validateMatchCreation(p1, null, dto, Optional.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateRatingGap: lanza excepción cuando la diferencia excede el máximo permitido")
    void validateRatingGap_ExceedsMax_Throws() {
        Player p1 = playerWithRating(1L, 1500.0);
        Player p2 = playerWithRating(2L, 1500.0 + MatchRuleValidator.MAX_LOCATION_RATING_GAP + 0.1);

        assertThatThrownBy(() -> validator.validateRatingGap(p1, p2))
                .isInstanceOf(InvalidMatchStateException.class);
    }

    @Test
    @DisplayName("validateRatingGap: no lanza excepción justo en el límite permitido")
    void validateRatingGap_AtMax_DoesNotThrow() {
        Player p1 = playerWithRating(1L, 1500.0);
        Player p2 = playerWithRating(2L, 1500.0 + MatchRuleValidator.MAX_LOCATION_RATING_GAP);

        assertThatCode(() -> validator.validateRatingGap(p1, p2)).doesNotThrowAnyException();
    }
}

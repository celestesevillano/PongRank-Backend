package org.example.pongrankbackend.Federation;

import org.example.pongrankbackend.Player.Gender;
import org.example.pongrankbackend.Player.InitialLevel;
import org.example.pongrankbackend.Player.dto.PlayerRegisterRequestDTO;
import org.example.pongrankbackend.Player.service.AgeCategorySuggester;
import org.example.pongrankbackend.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FederatedReviewServiceTest {
    private final FederatedReviewService service = new FederatedReviewService(null, null, null, new AgeCategorySuggester());

    @Test
    void normalizesMatchingCategory() {
        var input = declaration("  damas todo competidor  ", InitialLevel.EXPERT);
        service.validateRegistration(input);
        assertThat(input.getCategoryFdptm()).isEqualTo("Damas todo competidor");
    }

    @Test
    void rejectsCategoryFromAnotherDivision() {
        assertThatThrownBy(() -> service.validateRegistration(declaration("Varones todo competidor", InitialLevel.ADVANCED)))
                .isInstanceOf(ApiException.class).hasMessageContaining("no coincide");
    }

    @Test
    void rejectsDeclarationBelowAdvanced() {
        assertThatThrownBy(() -> service.validateRegistration(declaration("Damas todo competidor", InitialLevel.INTERMEDIATE)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Avanzado");
    }

    private PlayerRegisterRequestDTO declaration(String category, InitialLevel level) {
        return PlayerRegisterRequestDTO.builder().birthDate(LocalDate.now().minusYears(23))
                .gender(Gender.FEMALE).initialLevel(level).federatedDeclared(true).categoryFdptm(category).build();
    }
}

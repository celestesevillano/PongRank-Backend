package org.example.pongrankbackend.Player.service;

import org.example.pongrankbackend.Player.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AgeCategorySuggesterTest {

    private final AgeCategorySuggester suggester = new AgeCategorySuggester();
    private final LocalDate today = LocalDate.of(2026, 10, 19);

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @DisplayName("suggest: elige la categoría según la edad que se cumple en el año")
    @CsvSource({
            "2024-01-01, MALE,   Varones 07 años",
            "2019-12-31, FEMALE, Damas 07 años",
            "2017-06-01, FEMALE, Damas 09 años",
            "2014-06-01, MALE,   Varones 13 años",
            "2012-05-20, MALE,   Varones 15 años",
            "2008-02-02, FEMALE, Damas 19 años",
            "2005-02-02, MALE,   Varones todo competidor",
            "1996-01-01, FEMALE, Damas 30 años",
            "1980-09-09, MALE,   Varones 40 años",
            "1950-01-01, FEMALE, Damas 60 años",
            "1940-01-01, MALE,   Varones 80 años",
            "1920-01-01, MALE,   Varones 80 años",
            "2014-06-01, PREFER_NOT_TO_SAY, Damas o Varones 13 años",
            "2005-02-02, PREFER_NOT_TO_SAY, Todo competidor",
            "1960-01-01, PREFER_NOT_TO_SAY, Damas o Varones 60 años"
    })
    void suggest_picksCategoryByAge(LocalDate birthDate, Gender gender, String expected) {
        assertThat(suggester.suggest(birthDate, gender, today)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("ageInYear: usa el año de nacimiento, no el cumpleaños")
    @CsvSource({"2000-12-31, 26", "2000-01-01, 26"})
    void ageInYear_usesYearOfBirth(LocalDate birthDate, int expected) {
        assertThat(suggester.ageInYear(birthDate, today)).isEqualTo(expected);
    }
}

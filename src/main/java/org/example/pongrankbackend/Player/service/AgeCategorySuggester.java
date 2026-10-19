package org.example.pongrankbackend.Player.service;

import org.example.pongrankbackend.Player.Gender;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Sugiere la categoría del ranking nacional a partir de la edad que se cumple en el año en curso.
 * Es un supuesto propio del proyecto, no la regla oficial de la federación.
 */
@Component
public class AgeCategorySuggester {

    private static final List<Integer> YOUTH_LIMITS = List.of(7, 9, 11, 13, 15, 19);
    private static final int FIRST_VETERAN_AGE = 30;
    private static final int MAX_VETERAN_BAND_MEN = 80;
    private static final int MAX_VETERAN_BAND_WOMEN = 60;

    public int ageInYear(LocalDate birthDate, LocalDate today) {
        return today.getYear() - birthDate.getYear();
    }

    public String suggest(LocalDate birthDate, Gender gender, LocalDate today) {
        int age = ageInYear(birthDate, today);
        String prefix = gender.getRankingLabel();

        for (int limit : YOUTH_LIMITS) {
            if (age <= limit) {
                return String.format("%s %02d años", prefix, limit);
            }
        }
        if (age < FIRST_VETERAN_AGE) {
            return gender == Gender.PREFER_NOT_TO_SAY ? "Todo competidor" : prefix + " todo competidor";
        }
        int maxBand = gender == Gender.FEMALE ? MAX_VETERAN_BAND_WOMEN : MAX_VETERAN_BAND_MEN;
        int band = Math.min((age / 10) * 10, maxBand);
        return prefix + " " + band + " años";
    }
}

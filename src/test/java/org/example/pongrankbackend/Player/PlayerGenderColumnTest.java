package org.example.pongrankbackend.Player;

import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerGenderColumnTest {

    @Test
    @DisplayName("La columna gender es lo bastante larga para el valor más largo del enum Gender")
    void genderColumnLength_CoversLongestEnumValue() throws NoSuchFieldException {
        Field field = Player.class.getDeclaredField("gender");
        int declaredLength = field.getAnnotation(Column.class).length();

        int longestValue = Arrays.stream(Gender.values())
                .mapToInt(gender -> gender.name().length())
                .max()
                .orElseThrow();

        assertThat(declaredLength).isGreaterThanOrEqualTo(longestValue);
    }
}

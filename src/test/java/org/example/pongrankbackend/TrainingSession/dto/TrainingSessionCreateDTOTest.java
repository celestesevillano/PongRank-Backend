package org.example.pongrankbackend.TrainingSession.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingSessionCreateDTOTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private TrainingSessionCreateDTO.TrainingSessionCreateDTOBuilder validBuilder() {
        return TrainingSessionCreateDTO.builder()
                .swingVelocityMax(12.5)
                .postureScore(80.0)
                .kneeFlexionAngle(90.0)
                .shoulderRotationAngle(45.0)
                .durationSeconds(300);
    }

    @Test
    @DisplayName("DTO válido no genera violaciones")
    void validDto_HasNoViolations() {
        assertThat(validator.validate(validBuilder().build())).isEmpty();
    }

    @Test
    @DisplayName("rechaza swingVelocityMax negativo o irrealmente alto")
    void invalidSwingVelocityMax_HasViolation() {
        assertThat(validator.validate(validBuilder().swingVelocityMax(-1.0).build())).isNotEmpty();
        assertThat(validator.validate(validBuilder().swingVelocityMax(500.0).build())).isNotEmpty();
    }

    @Test
    @DisplayName("rechaza kneeFlexionAngle fuera de 0-180 grados")
    void invalidKneeFlexionAngle_HasViolation() {
        assertThat(validator.validate(validBuilder().kneeFlexionAngle(-10.0).build())).isNotEmpty();
        assertThat(validator.validate(validBuilder().kneeFlexionAngle(200.0).build())).isNotEmpty();
    }

    @Test
    @DisplayName("rechaza shoulderRotationAngle fuera de -180 a 180 grados")
    void invalidShoulderRotationAngle_HasViolation() {
        assertThat(validator.validate(validBuilder().shoulderRotationAngle(-200.0).build())).isNotEmpty();
        assertThat(validator.validate(validBuilder().shoulderRotationAngle(200.0).build())).isNotEmpty();
    }

    @Test
    @DisplayName("rechaza durationSeconds mayor a 4 horas")
    void invalidDurationSeconds_TooLong_HasViolation() {
        Set<ConstraintViolation<TrainingSessionCreateDTO>> violations =
                validator.validate(validBuilder().durationSeconds(20000).build());

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("rechaza durationSeconds menor a 1 segundo")
    void invalidDurationSeconds_TooShort_HasViolation() {
        assertThat(validator.validate(validBuilder().durationSeconds(0).build())).isNotEmpty();
    }
}

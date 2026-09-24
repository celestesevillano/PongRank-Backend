package org.example.pongrankbackend.Tournament.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Tournament.TournamentType;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentCreateRequestDTO {

    @NotNull(message = "El ID del club organizador es obligatorio")
    private Long clubId;

    @NotBlank(message = "El nombre del torneo es obligatorio")
    @Size(max = 150, message = "El nombre del torneo no debe exceder 150 caracteres")
    private String name;

    @NotNull(message = "El tipo de torneo es obligatorio (INTERNAL u OPEN)")
    private TournamentType type;

    @NotNull(message = "El formato de partido es obligatorio (BO3, BO5 o BO7)")
    private MatchFormat matchFormat;

    private LocalDateTime startDate;

    private LocalDateTime endDate;
}

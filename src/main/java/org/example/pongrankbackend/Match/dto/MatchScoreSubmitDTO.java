package org.example.pongrankbackend.Match.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.MatchSet.dto.MatchSetRequestDTO;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchScoreSubmitDTO {

    @NotEmpty(message = "La lista de sets no puede estar vacía")
    @Valid
    private List<MatchSetRequestDTO> sets;
}

package org.example.pongrankbackend.Tournament.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Used for manual seeding (all participants, seed 1 first) and for resolving a tie (tied players, best first)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentOrderedPlayersRequestDTO {

    @NotEmpty(message = "La lista ordenada de jugadores es obligatoria")
    private List<@NotNull Long> orderedPlayerIds;
}

package org.example.pongrankbackend.Tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentParticipantResponseDTO {

    private Long id;
    private PlayerSummaryDTO player;
    private Integer seed;
    private Integer groupNumber;
}

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
public class GroupStandingRowDTO {

    private Integer position;
    private PlayerSummaryDTO player;
    private Integer played;
    private Integer wins;
    private Integer losses;
    private Integer setDifference;
    private Integer pointDifference;
    private Boolean unresolvedTie;
    private Boolean qualified;
}

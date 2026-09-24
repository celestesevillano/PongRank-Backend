package org.example.pongrankbackend.MatchSet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchSetResponseDTO {

    private Long id;
    private Integer setNumber;
    private Integer scorePlayer1;
    private Integer scorePlayer2;
    private Integer winnerPlayerNumber; // 1 or 2
}

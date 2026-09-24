package org.example.pongrankbackend.Tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Tournament.TournamentMatchStatus;
import org.example.pongrankbackend.Tournament.TournamentStage;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentMatchResponseDTO {

    private Long id;
    private TournamentStage stage;
    private Integer groupNumber;
    private Integer round;
    private Integer bracketPosition;
    private PlayerSummaryDTO player1;
    private PlayerSummaryDTO player2;
    private Long matchId;
    private TournamentMatchStatus status;
    private PlayerSummaryDTO winner;
    private Integer setsPlayer1;
    private Integer setsPlayer2;
    private Integer pointsPlayer1;
    private Integer pointsPlayer2;
}

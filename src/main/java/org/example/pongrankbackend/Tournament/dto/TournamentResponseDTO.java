package org.example.pongrankbackend.Tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;
import org.example.pongrankbackend.Tournament.TournamentStatus;
import org.example.pongrankbackend.Tournament.TournamentType;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentResponseDTO {

    private Long id;
    private String name;
    private Long clubId;
    private String clubName;
    private TournamentType type;
    private MatchFormat matchFormat;
    private TournamentStatus status;
    private Boolean manualSeeding;
    private PlayerSummaryDTO winner;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

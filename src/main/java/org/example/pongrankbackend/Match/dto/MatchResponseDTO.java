package org.example.pongrankbackend.Match.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchStatus;
import org.example.pongrankbackend.Match.MatchType;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchResponseDTO {

    private Long id;
    private PlayerSummaryDTO player1;
    private PlayerSummaryDTO player2;
    private Long communityId;
    private String communityName;
    private Long tournamentId;
    private String tournamentName;
    private MatchFormat format;
    private MatchType matchType;
    private MatchStatus status;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String scoreSummary;
    private Long winnerId;
    private Double ratingDeltaP1;
    private Double ratingDeltaP2;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
}

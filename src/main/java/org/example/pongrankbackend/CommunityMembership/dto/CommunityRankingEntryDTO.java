package org.example.pongrankbackend.CommunityMembership.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityRankingEntryDTO {
    private Integer position;
    private Long playerId;
    private String playerName;
    private Double ratingGlicko;
    private Double ratingDeviation;
}
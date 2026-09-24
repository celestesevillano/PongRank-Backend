package org.example.pongrankbackend.Match.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Match.MatchFormat;
import org.example.pongrankbackend.Match.MatchType;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchCreateRequestDTO {

    private Long opponentId;

    @Builder.Default
    private MatchFormat format = MatchFormat.BO3;

    @NotNull(message = "El tipo de emparejamiento (matchType) es obligatorio: FRIEND, COMMUNITY, LOCATION o TOURNAMENT")
    private MatchType matchType;

    private Long communityId;

    private Long tournamentId;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private BigDecimal opponentLatitude;

    private BigDecimal opponentLongitude;
}

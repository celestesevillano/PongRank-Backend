package org.example.pongrankbackend.Tournament.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupStandingsResponseDTO {

    private Integer groupNumber;
    private Boolean completed;
    private Boolean blockedByTie;
    private List<GroupStandingRowDTO> standings;
    private List<List<Long>> unresolvedTies;
}

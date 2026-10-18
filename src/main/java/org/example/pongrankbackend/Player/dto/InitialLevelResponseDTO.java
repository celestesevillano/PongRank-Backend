package org.example.pongrankbackend.Player.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Player.InitialLevel;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitialLevelResponseDTO {

    private InitialLevel level;
    private String label;
}

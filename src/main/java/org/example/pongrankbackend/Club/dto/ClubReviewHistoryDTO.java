package org.example.pongrankbackend.Club.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Club.ClubStatus;
import org.example.pongrankbackend.Player.dto.PlayerSummaryDTO;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClubReviewHistoryDTO {

    private Long id;
    private ClubStatus result;
    private String reason;
    private String affiliationDocumentUrl;
    private PlayerSummaryDTO reviewer;
    private LocalDateTime reviewedAt;
}

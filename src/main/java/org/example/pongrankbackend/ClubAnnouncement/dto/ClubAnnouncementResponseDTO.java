package org.example.pongrankbackend.ClubAnnouncement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClubAnnouncementResponseDTO {

    private Long id;
    private Long clubId;
    private String title;
    private String body;
    private String authorName;
    private LocalDateTime createdAt;
}

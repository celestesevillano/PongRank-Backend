package org.example.pongrankbackend.ClubAnnouncement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClubAnnouncementRequestDTO {

    @NotBlank
    @Size(max = 120)
    private String title;

    @NotBlank
    @Size(max = 1000)
    private String body;
}

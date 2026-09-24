package org.example.pongrankbackend.Community.dto;

import lombok.*;
import org.example.pongrankbackend.Community.CommunityStatus;
import org.example.pongrankbackend.Community.CommunityType;
import org.example.pongrankbackend.CommunityMembership.CommunityRole;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityDetailResponseDTO {
    private Long id;
    private String name;
    private String description;
    private CommunityType communityType;
    private CommunityStatus status;
    private Long memberCount;
    private Long creatorId;
    private String creatorName;
    private Boolean isMember;
    private CommunityRole myRole;
    private LocalDateTime createdAt;
}

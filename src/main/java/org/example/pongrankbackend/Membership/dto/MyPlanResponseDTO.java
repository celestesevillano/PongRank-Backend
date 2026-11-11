package org.example.pongrankbackend.Membership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Membership.MembershipPlan;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyPlanResponseDTO {

    private MembershipPlan plan;
    // null si el plan es FREEMIUM
    private LocalDateTime endDate;
    private boolean coachAccess;
    private boolean canCreateClub;
    private int maxCommunitiesCreated;
    private int maxCommunitiesTotal;
    private int maxClubsTotal;
    private int maxSpaces;
    private long usedSpaces;
}

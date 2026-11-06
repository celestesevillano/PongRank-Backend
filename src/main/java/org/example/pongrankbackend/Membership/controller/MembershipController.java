package org.example.pongrankbackend.Membership.controller;

import org.example.pongrankbackend.Membership.dto.MyPlanResponseDTO;
import org.example.pongrankbackend.Membership.service.MyPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memberships")
public class MembershipController {

    private final MyPlanService myPlanService;

    public MembershipController(MyPlanService myPlanService) {
        this.myPlanService = myPlanService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MyPlanResponseDTO> getMyPlan() {
        return ResponseEntity.ok(myPlanService.getMyPlan());
    }
}

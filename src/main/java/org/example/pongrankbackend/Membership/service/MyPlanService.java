package org.example.pongrankbackend.Membership.service;

import org.example.pongrankbackend.Membership.dto.MyPlanResponseDTO;

public interface MyPlanService {

    // Plan vigente del jugador autenticado, con lo que incluye y sus límites
    MyPlanResponseDTO getMyPlan();
}

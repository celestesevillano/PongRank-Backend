package org.example.pongrankbackend.common.exception;

import org.springframework.http.HttpStatus;

// Se lanza cuando el plan del jugador no alcanza para una acción (crear comunidades/club, usar el Coach),
// separada de UnauthorizedActionException para que el frontend distinga "necesitas upgrade" de "no tienes permiso".
public class PlanRestrictionException extends ApiException {
    public PlanRestrictionException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}

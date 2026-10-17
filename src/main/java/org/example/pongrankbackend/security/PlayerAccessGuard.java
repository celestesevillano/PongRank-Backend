package org.example.pongrankbackend.security;

import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.springframework.stereotype.Component;

@Component
public class PlayerAccessGuard {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    public void requireSelfOrAdmin(Long playerId, String deniedMessage) {
        Long currentUserId = SecurityUtils.getRequiredCurrentUserId();
        if (!currentUserId.equals(playerId) && !SecurityUtils.hasRole(SYSTEM_ADMIN)) {
            throw new UnauthorizedActionException(deniedMessage);
        }
    }
}

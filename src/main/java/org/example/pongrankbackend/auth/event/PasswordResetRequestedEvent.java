package org.example.pongrankbackend.auth.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PasswordResetRequestedEvent extends ApplicationEvent {

    private final Long playerId;
    private final String resetLink;
    private final int expirationMinutes;

    public PasswordResetRequestedEvent(Object source, Long playerId, String resetLink, int expirationMinutes) {
        super(source);
        this.playerId = playerId;
        this.resetLink = resetLink;
        this.expirationMinutes = expirationMinutes;
    }
}

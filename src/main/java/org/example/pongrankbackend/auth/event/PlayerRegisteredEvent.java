package org.example.pongrankbackend.auth.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PlayerRegisteredEvent extends ApplicationEvent {

    private final Long playerId;

    public PlayerRegisteredEvent(Object source, Long playerId) {
        super(source);
        this.playerId = playerId;
    }
}

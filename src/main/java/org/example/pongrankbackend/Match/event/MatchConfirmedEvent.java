package org.example.pongrankbackend.Match.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class MatchConfirmedEvent extends ApplicationEvent {

    private final Long matchId;
    private final boolean player1Won;

    public MatchConfirmedEvent(Object source, Long matchId, boolean player1Won) {
        super(source);
        this.matchId = matchId;
        this.player1Won = player1Won;
    }
}

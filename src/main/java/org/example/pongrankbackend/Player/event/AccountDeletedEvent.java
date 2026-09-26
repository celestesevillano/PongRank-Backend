package org.example.pongrankbackend.Player.event;

import lombok.Getter;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.springframework.context.ApplicationEvent;

@Getter
public class AccountDeletedEvent extends ApplicationEvent {

    // El email/nombre se capturan aquí porque para cuando este evento se procese (después del
    // commit), deleteAccount ya mutó el email del jugador para liberar la restricción de unicidad
    private final String playerName;
    private final String playerEmail;
    private final MembershipPlan plan;

    public AccountDeletedEvent(Object source, String playerName, String playerEmail, MembershipPlan plan) {
        super(source);
        this.playerName = playerName;
        this.playerEmail = playerEmail;
        this.plan = plan;
    }
}

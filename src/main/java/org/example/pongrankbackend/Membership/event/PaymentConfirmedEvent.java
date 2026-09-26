package org.example.pongrankbackend.Membership.event;

import lombok.Getter;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

@Getter
public class PaymentConfirmedEvent extends ApplicationEvent {

    private final Long playerId;
    private final MembershipPlan plan;
    private final BigDecimal amount;
    private final String transactionId;

    public PaymentConfirmedEvent(Object source, Long playerId, MembershipPlan plan, BigDecimal amount, String transactionId) {
        super(source);
        this.playerId = playerId;
        this.plan = plan;
        this.amount = amount;
        this.transactionId = transactionId;
    }
}

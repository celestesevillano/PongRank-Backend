package org.example.pongrankbackend.Player.event;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.email.service.EmailService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AccountDeletedEventListener {

    private final EmailService emailService;

    public AccountDeletedEventListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccountDeleted(AccountDeletedEvent event) {
        // Objeto transitorio solo para transportar nombre/email al template; nunca se persiste
        Player snapshot = Player.builder()
                .name(event.getPlayerName())
                .email(event.getPlayerEmail())
                .build();
        emailService.sendAccountDeletedEmail(snapshot, event.getPlan());
    }
}

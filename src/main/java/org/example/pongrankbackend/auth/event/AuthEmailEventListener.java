package org.example.pongrankbackend.auth.event;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.email.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuthEmailEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuthEmailEventListener.class);

    private final PlayerRepository playerRepository;
    private final EmailService emailService;

    public AuthEmailEventListener(PlayerRepository playerRepository, EmailService emailService) {
        this.playerRepository = playerRepository;
        this.emailService = emailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void onPlayerRegistered(PlayerRegisteredEvent event) {
        playerRepository.findById(event.getPlayerId()).ifPresentOrElse(
                emailService::sendWelcomeEmail,
                () -> log.warn("No se encontró el jugador ID {} para enviar el correo de bienvenida", event.getPlayerId())
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        Player player = playerRepository.findById(event.getPlayerId()).orElse(null);
        if (player == null) {
            log.warn("No se encontró el jugador ID {} para enviar el correo de recuperación de contraseña", event.getPlayerId());
            return;
        }
        emailService.sendPasswordResetEmail(player, event.getResetLink(), event.getExpirationMinutes());
    }
}

package org.example.pongrankbackend.auth.event;

import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.email.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthEmailEventListenerTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthEmailEventListener listener;

    @Test
    @DisplayName("onPlayerRegistered: envía el correo de bienvenida al jugador registrado")
    void onPlayerRegistered_SendsWelcomeEmail() {
        Player player = Player.builder().id(1L).name("Alice").email("alice@utec.edu.pe").build();
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

        listener.onPlayerRegistered(new PlayerRegisteredEvent(this, 1L));

        verify(emailService).sendWelcomeEmail(player);
    }

    @Test
    @DisplayName("onPlayerRegistered: si el jugador ya no existe, no falla ni envía nada")
    void onPlayerRegistered_PlayerNotFound_DoesNothing() {
        when(playerRepository.findById(1L)).thenReturn(Optional.empty());

        listener.onPlayerRegistered(new PlayerRegisteredEvent(this, 1L));

        verify(emailService, never()).sendWelcomeEmail(any());
    }

    @Test
    @DisplayName("onPasswordResetRequested: envía el correo de recuperación con el link y minutos correctos")
    void onPasswordResetRequested_SendsResetEmail() {
        Player player = Player.builder().id(1L).name("Alice").email("alice@utec.edu.pe").build();
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

        listener.onPasswordResetRequested(new PasswordResetRequestedEvent(this, 1L, "http://localhost/reset?token=abc", 30));

        verify(emailService).sendPasswordResetEmail(eq(player), eq("http://localhost/reset?token=abc"), eq(30));
    }

    @Test
    @DisplayName("onPasswordResetRequested: si el jugador ya no existe, no falla ni envía nada")
    void onPasswordResetRequested_PlayerNotFound_DoesNothing() {
        when(playerRepository.findById(1L)).thenReturn(Optional.empty());

        listener.onPasswordResetRequested(new PasswordResetRequestedEvent(this, 1L, "http://localhost/reset", 30));

        verify(emailService, never()).sendPasswordResetEmail(any(), any(), anyInt());
    }
}
